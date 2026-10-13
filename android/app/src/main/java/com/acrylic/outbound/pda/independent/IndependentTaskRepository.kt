package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Callable

internal class IndependentTaskRepository(private val db: PdaDatabase, private val sample: () -> TimeSample,
    private val actor: () -> IndependentTaskActor) {
    private val dao get() = db.independentTasks()
    private val catalog get() = IndependentSkuRepository(db, sample)
    fun activeProducts(): Pair<IndependentSkuImport, List<IndependentSkuBale>> {
        val review = checkNotNull(catalog.active()) { "请先选择、预检并启用原sku资料" }
        return review.imported to IndependentTaskCatalog.products(review)
    }
    fun latestRequest(): IndependentTaskRequest? {
        val owner = actor()
        return dao.latestRequest(owner.operatorId, owner.deviceId, owner.warehouseId)
    }
    fun history(before: Long = Long.MAX_VALUE): List<IndependentTaskHistory> {
        val owner = actor()
        return dao.page(owner.operatorId, owner.deviceId, owner.warehouseId, before)
    }
    fun requests(before: Long = Long.MAX_VALUE): List<IndependentTaskRequestHistory> {
        val owner = actor()
        return dao.requestPage(owner.operatorId, owner.deviceId, owner.warehouseId, before)
    }
    fun request(id: String): Pair<IndependentTaskCommand, IndependentTaskView?> {
        val original = checkNotNull(dao.request(id)); actor().requireOwner(original)
        return IndependentTaskCommand.read(JSONObject(original.commandJson)) to dao.task(id)?.let { read(it.id) }
    }
    fun read(id: String): IndependentTaskView = db.runInTransaction(Callable {
        val task = checkNotNull(dao.task(id)) { "原任务尚未创建；请按原请求重试" }
        actor().requireOwner(task)
        IndependentTaskView(task, dao.lines(id), checkNotNull(dao.state(id)), dao.events(id))
    })
    fun prepare(input: IndependentTaskCommand, grant: IndependentCaptureGrant): IndependentTaskRequest = UpdateGate.business {
        val command = input.copy(quantities = input.quantities.toSortedMap()); command.validate()
        db.runInTransaction(Callable {
            val authorization = grant.snapshot()
            dao.request(command.id)?.let { previous ->
                actor().requireOwner(previous)
                check(previous.commandSha256 == command.sha()) { "原请求已固定，不能更换车号、原因、资料或产品计划" }
                IndependentCredentialSnapshot.read(previous.authorizationJson).requireSame(authorization)
                return@Callable previous
            }
            val review = catalog.review(command.importId)
            check(review.activeImportId == command.importId) { "使用中的资料已变化，请显式新建任务" }
            IndependentTaskCatalog.lines(command, review)
            val identity = authorization.proof.identity
            val request = IndependentTaskRequest(command.id, command.importId, command.json().toString(), command.sha(),
                authorization.json(), identity.operatorId, identity.deviceId, identity.warehouseId)
            actor().requireOwner(request); authorization.requireSame(grant.snapshot())
            dao.insertRequest(request); request
        })
    }
    fun create(command: IndependentTaskCommand, grant: IndependentCaptureGrant): IndependentTaskView = UpdateGate.business {
        val request = prepare(command, grant) // Separate durable transaction: preserve the original input on a later failure.
        db.runInTransaction {
            val authorization = IndependentCredentialSnapshot.read(request.authorizationJson)
            val current = grant.snapshot(); authorization.requireSame(current); actor().requireOwner(request)
            dao.task(command.id)?.let {
                check(it.commandSha256 == request.commandSha256); return@runInTransaction
            }
            check(current.proof.originalToken == authorization.proof.originalToken) { "原请求凭据已替换；保留原请求并显式新建任务" }
            val time = sample(); authorization.proof.at(TrustedTime.now(JSONObject(authorization.timeAnchorJson), time))
            val original = IndependentTaskCommand.read(JSONObject(request.commandJson))
            check(original.sha() == request.commandSha256)
            val review = catalog.review(original.importId)
            val task = task(Creation(original, review.imported.sha256, authorization, time))
            dao.insertTask(task); dao.insertLines(IndependentTaskCatalog.lines(original, review))
            dao.insertState(IndependentTaskState(task.id, nextEventSeq = 2))
            check(grant.recheck().originalToken == authorization.proof.originalToken)
            dao.insertEvent(IndependentTaskEvent(UUID.randomUUID().toString(), task.id, 1, "CREATE_PLANNED",
                request.commandJson, time.wall, time.elapsed, time.boot))
        }
        read(request.id)
    }
    private data class Creation(val command: IndependentTaskCommand, val sha: String,
        val authorization: IndependentCredentialSnapshot, val time: TimeSample)
    private fun task(creation: Creation): IndependentTask {
        val (command, sha, authorization, time) = creation
        val proof = authorization.proof; val identity = proof.identity
        return IndependentTask(command.id, command.importId, sha, proof.id, proof.originalToken, authorization.trustedKeyJson,
            authorization.timeAnchorJson, authorization.localSessionId, identity.operatorId, identity.deviceId, identity.warehouseId,
            identity.serverRecoveryEpoch, command.vehicle, command.reason, command.total(), command.sha(), time.wall, time.elapsed, time.boot)
    }
}
