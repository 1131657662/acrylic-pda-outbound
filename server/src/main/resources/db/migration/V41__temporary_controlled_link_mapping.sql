SET SESSION lock_wait_timeout=10;
-- V34 declared the mapping origin check inline, so MySQL auto-named it. Resolve the real name instead of guessing;
-- the guard fails loudly if the constraint is absent, and everything else precedes in one statement.
SET @v41_origin := (SELECT tc.CONSTRAINT_NAME
 FROM information_schema.TABLE_CONSTRAINTS tc
 JOIN information_schema.CHECK_CONSTRAINTS cc
   ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME
 WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='temporary_source_mapping'
   AND tc.CONSTRAINT_TYPE='CHECK' AND cc.CHECK_CLAUSE LIKE '%origin%');
CREATE TEMPORARY TABLE r06_v41_guard (valid INT NOT NULL, CONSTRAINT ck_v41_origin_preflight CHECK(valid=1));
INSERT INTO r06_v41_guard SELECT IF(@v41_origin IS NULL,0,1);
DROP TEMPORARY TABLE r06_v41_guard;
SET @v41_alter := CONCAT('ALTER TABLE temporary_source_mapping DROP CHECK `',@v41_origin,
 '`, ADD CONSTRAINT ck_source_mapping_origin CHECK(origin IN (''MIGRATED_BASELINE'',''INITIAL_ACCEPTANCE'',''SOURCE_REGISTERED'',''CONTROLLED_LINK''))');
PREPARE v41_statement FROM @v41_alter;
EXECUTE v41_statement;
DEALLOCATE PREPARE v41_statement;
