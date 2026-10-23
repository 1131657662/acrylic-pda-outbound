package com.acrylic.outbound.recovery;

/** Fixed SQL only: one consistent read, bounded arrays, no join fan-out or pool-peer observation. */
final class RecoverySuccessorAdmissionSql {
    private RecoverySuccessorAdmissionSql() { }

    static final String SELECT = """
        SELECT JSON_OBJECT('actualServer',@@server_uuid,'actualSchema',DATABASE(),
          'mode',s.service_mode,'numeric',s.recovery_epoch,
          'binding',(SELECT JSON_OBJECT('id',id,'installation_id',BIN_TO_UUID(installation_id),
            'mysql_server_uuid',BIN_TO_UUID(mysql_server_uuid),'database_schema',database_schema,
            'baseline_numeric_epoch',baseline_numeric_epoch,'initial_control_sha256',LOWER(HEX(initial_control_sha256)),
            'registered_at',DATE_FORMAT(registered_at,'%Y-%m-%dT%H:%i:%s.%f'))
            FROM recovery_installation_binding WHERE id=1),
          'initialNormal',(SELECT JSON_OBJECT('id',id,'format_version',format_version,
            'installation_id',BIN_TO_UUID(installation_id),'authority_revision',authority_revision,
            'control_revision',control_revision,'control_record_sha256',LOWER(HEX(control_record_sha256)),
            'previous_record_sha256',LOWER(HEX(previous_record_sha256)),'intent_sha256',LOWER(HEX(intent_sha256)),
            'numeric_epoch',numeric_epoch,'operator_id',operator_id,
            'registered_at',DATE_FORMAT(registered_at,'%Y-%m-%dT%H:%i:%s.%f'))
            FROM recovery_normal_admission WHERE id=1),
          'projections',(SELECT JSON_ARRAYAGG(JSON_OBJECT('installation',BIN_TO_UUID(installation_id),
            'revision',control_revision,'hash',LOWER(HEX(record_sha256)),
            'previous',LOWER(HEX(previous_record_sha256)),'epoch',BIN_TO_UUID(epoch_id),
            'numeric',numeric_epoch,'phase',control_phase))
            FROM (SELECT * FROM recovery_control_projection LIMIT 100001) p),
          'epochs',(SELECT JSON_ARRAYAGG(JSON_OBJECT('installation',BIN_TO_UUID(installation_id),
            'epoch',BIN_TO_UUID(epoch_id),'numeric',numeric_epoch,'revision',first_control_revision,
            'hash',LOWER(HEX(first_control_sha256))))
            FROM (SELECT * FROM recovery_control_epoch LIMIT 100001) e),
          'bindings',(SELECT JSON_ARRAYAGG(JSON_OBJECT('formatVersion',1,'installationId',BIN_TO_UUID(installation_id),
            'startRevision',start_control_revision,'startHash',LOWER(HEX(start_control_sha256)),
            'predecessorRelease',LOWER(HEX(predecessor_release_sha256)),
            'predecessorIntent',LOWER(HEX(predecessor_intent_sha256)),
            'sourceBinding',LOWER(HEX(source_binding_sha256)),'sourceNormal',LOWER(HEX(source_normal_sha256)),
            'serverUuid',BIN_TO_UUID(mysql_server_uuid),'schema',database_schema,'bindingHash',LOWER(HEX(binding_sha256))))
            FROM (SELECT * FROM recovery_successor_binding LIMIT 100001) b),
          'normals',(SELECT JSON_ARRAYAGG(JSON_OBJECT('formatVersion',1,'installationId',BIN_TO_UUID(installation_id),
            'bindingRevision',binding_revision,'revision',control_revision,'recordHash',LOWER(HEX(control_record_sha256)),
            'previousHash',LOWER(HEX(previous_record_sha256)),'intentHash',LOWER(HEX(intent_sha256)),
            'numeric',numeric_epoch,'operator',operator_id,'normalHash',LOWER(HEX(normal_sha256))))
            FROM (SELECT * FROM recovery_successor_normal LIMIT 100001) n))
        FROM server_state s WHERE s.id=1
        """;
}
