-- Candidate read-only aggregate query for a separately authorized V28 environment.
-- Run with a SELECT-only account and protect its option file; no payload/token/error text.
SET time_zone="+00:00";
SELECT COUNT(*),
    COALESCE(SUM(status='FAILED'),0),
    COALESCE(SUM(status='RUNNING' AND lease_until<UTC_TIMESTAMP(3)),0),
    COALESCE(SUM(status IN ('PENDING','RETRY') AND available_at<=UTC_TIMESTAMP(3)),0),
    COALESCE(MAX(CASE WHEN status IN ('PENDING','RETRY') THEN
      GREATEST(TIMESTAMPDIFF(SECOND,available_at,UTC_TIMESTAMP(3)),0) ELSE 0 END),0),
    COALESCE(SUM(status='RETRY'),0) FROM background_job;
