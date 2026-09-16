-- 남은 테스트 데이터 정리용 SQL (실행 안 함 / 커밋 대상 아님 / 배포 산출물 아님).
-- 과거 버그(meeting/project 삭제 409)로 인해 삭제되지 못하고 남은 meetings/ai_analysis_runs 행을 정리하기 위한 스크립트다.
-- 반드시 각 DELETE 전 SELECT로 대상을 먼저 눈으로 확인한 뒤, 대상 id를 아래 :target_meeting_ids 에 채워서 실행할 것.
-- 순서: meeting_action_links -> decisions -> action_items(해당 meeting) -> ai_analysis_runs -> meetings
-- (meeting_action_links.action_item_id, decisions.source_analysis_id/action_items.source_analysis_id 가
--  각각 action_items/ai_analysis_runs를 참조하므로 이 순서를 지켜야 FK 위반이 나지 않는다.)

-- 0. 정리 대상 회의 id를 먼저 확정한다 (실제 테스트 데이터인지 반드시 육안 확인 후 진행).
SELECT id, project_id, title, scheduled_at, created_at
FROM meetings
WHERE id IN (/* target_meeting_ids */);

-- 1. meeting_action_links 확인 후 삭제
SELECT * FROM meeting_action_links WHERE meeting_id IN (/* target_meeting_ids */);
-- DELETE FROM meeting_action_links WHERE meeting_id IN (/* target_meeting_ids */);

-- 2. decisions 확인 후 삭제
SELECT * FROM decisions WHERE meeting_id IN (/* target_meeting_ids */);
-- DELETE FROM decisions WHERE meeting_id IN (/* target_meeting_ids */);

-- 3. action_items(해당 meeting에서 origin된 것만) 확인 후 삭제
SELECT * FROM action_items WHERE origin_meeting_id IN (/* target_meeting_ids */);
-- DELETE FROM action_items WHERE origin_meeting_id IN (/* target_meeting_ids */);

-- 4. ai_analysis_runs 확인 후 삭제
SELECT * FROM ai_analysis_runs WHERE meeting_id IN (/* target_meeting_ids */);
-- DELETE FROM ai_analysis_runs WHERE meeting_id IN (/* target_meeting_ids */);

-- 5. meeting_participants 확인 후 삭제
SELECT * FROM meeting_participants WHERE meeting_id IN (/* target_meeting_ids */);
-- DELETE FROM meeting_participants WHERE meeting_id IN (/* target_meeting_ids */);

-- 6. meetings 삭제 (마지막)
-- DELETE FROM meetings WHERE id IN (/* target_meeting_ids */);
