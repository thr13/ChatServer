-- postgre 이미지 생성시(initdb.sql 최초 실행시) chatuser(어드민 유저) 권한 설정
GRANT ALL PRIVILEGES ON DATABASE chatdb TO chatuser;

-- UUID 확장 모듈
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- pg_stat_statements 확장 모듈 (쿼리 성능 모니터링용)
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- 연결 풀 최적화
ALTER SYSTEM SET max_connections = '200'; -- DB 동시 접근 가능수
ALTER SYSTEM SET shared_buffers = '256MB'; -- 공유 메모리 공간 설정(DB 엔진 캐시 작업)
ALTER SYSTEM SET effective_cache_size = '1GB'; -- 쿼리 플래너가 사용할 수 있는 캐시 메모리 크기 설정
ALTER SYSTEM SET maintenance_work_mem = '64MB'; -- 인덱스 생성, 테이블 스키마 변경할 때 사용 가능한 메모리 크기 설정
ALTER SYSTEM SET checkpoint_completion_target = '0.9'; -- 체크포인트(데이터를 디스크에 주기적을 저장) 작업이 완료가 되는 목표 기준치
ALTER SYSTEM SET wal_buffers = '16MB'; -- wal 로깅 설정
ALTER SYSTEM SET default_statistics_target = '100';

-- 설정 리로드
SELECT pg_reload_conf();

-- 커스텀 함수: 'EXECUTE FUNCTION 함수명()' 으로 적용 가능
CREATE OR REPLACE FUNCTION update_modified_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';