package com.followup.actionitem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * V10 마이그레이션 시점에는 이미 스키마가 최신 버전까지 적용된 뒤라 실제 action_items 테이블에는
 * assignee_user_id 컬럼이 더 이상 없다(그래서 "이관 전/후"를 실제 스키마로 재현할 수 없다). 대신 V10의
 * INSERT 문을 파일에서 그대로 읽어, 같은 이름의 TEMPORARY TABLE(세션 동안 실제 테이블을 가린다) 위에서
 * 실행해 데이터 이관 로직 자체를 검증한다 — 실제 action_items/action_item_assignees에는 영향이 없다.
 */
@SpringBootTest
class AssigneeMigrationSqlTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationInsertMovesOnlyNonNullAssigneeUserIdRowsAndIgnoresExistingPairs() throws Exception {
        String migrationSql = Files.readString(Path.of(
                "src/main/resources/db/migration/V10__merge_assignee_into_assignees.sql"));
        String insertStatement = extractInsertStatement(migrationSql);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE action_items (id BIGINT, assignee_user_id BIGINT)");
            statement.execute("CREATE TEMPORARY TABLE action_item_assignees ("
                    + "action_item_id BIGINT, user_id BIGINT, PRIMARY KEY (action_item_id, user_id))");

            // 1: 담당자 있음(이관 대상), 2: 담당자 없음(제외), 3: 담당자 있음(이관 대상)
            statement.execute("INSERT INTO action_items VALUES (1, 100), (2, NULL), (3, 100)");
            // (1, 100)은 이미 옮겨져 있던 것처럼 미리 넣어 INSERT IGNORE의 중복 무시 동작도 함께 검증한다.
            statement.execute("INSERT INTO action_item_assignees VALUES (1, 100)");

            statement.execute(insertStatement);

            List<String> rows = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery(
                    "SELECT action_item_id, user_id FROM action_item_assignees ORDER BY action_item_id")) {
                while (rs.next()) {
                    rows.add(rs.getLong(1) + ":" + rs.getLong(2));
                }
            }

            assertThat(rows).containsExactly("1:100", "3:100");
        }
    }

    private String extractInsertStatement(String migrationSql) {
        return Arrays.stream(migrationSql.split(";"))
                .map(String::trim)
                .filter(s -> s.toUpperCase().startsWith("INSERT"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("V10 migration has no INSERT statement"));
    }
}
