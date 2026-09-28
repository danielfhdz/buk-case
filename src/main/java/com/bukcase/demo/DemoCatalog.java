package com.bukcase.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only lookups that make the console output readable (names instead of ids).
 */
@Component
class DemoCatalog {

    record DemoUser(long id, String username, String profiles) {
    }

    record Node(long id, String label, Long parentId) {
    }

    record ProfileRow(long id, String name, boolean system) {
    }

    private final JdbcTemplate jdbc;

    DemoCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<DemoUser> users() {
        return jdbc.query("""
                SELECT u.id, u.username, COALESCE(string_agg(p.name, ', ' ORDER BY p.name), '(no profile)') AS profiles
                FROM users u
                LEFT JOIN user_profiles up ON up.user_id = u.id
                LEFT JOIN profiles p ON p.id = up.profile_id
                WHERE u.username NOT LIKE 'test.%' AND u.username NOT LIKE 'bench.%'
                GROUP BY u.id, u.username
                ORDER BY u.id
                """, (rs, row) -> new DemoUser(rs.getLong("id"), rs.getString("username"), rs.getString("profiles")));
    }

    DemoUser user(long id) {
        return users().stream().filter(user -> user.id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown user " + id));
    }

    List<ProfileRow> profiles() {
        return jdbc.query("SELECT id, name, is_system FROM profiles ORDER BY id",
                (rs, row) -> new ProfileRow(rs.getLong("id"), rs.getString("name"), rs.getBoolean("is_system")));
    }

    List<Long> demoProfileIds() {
        return jdbc.queryForList("SELECT id FROM profiles WHERE name LIKE 'Demo %'", Long.class);
    }

    List<String> areaTree() {
        return tree(jdbc.query("SELECT id, name, parent_id FROM areas ORDER BY id",
                (rs, row) -> new Node(rs.getLong("id"), rs.getString("name"), nullableLong(rs.getObject("parent_id")))));
    }

    List<String> resourceTree() {
        return tree(jdbc.query("SELECT id, code, name, parent_id FROM resources ORDER BY id",
                (rs, row) -> new Node(rs.getLong("id"), rs.getString("code") + " (" + rs.getString("name") + ")",
                        nullableLong(rs.getObject("parent_id")))));
    }

    String areaName(Long areaId) {
        if (areaId == null) {
            return "(no area)";
        }
        return jdbc.queryForObject("SELECT name FROM areas WHERE id = ?", String.class, areaId);
    }

    String resourceCode(long resourceId) {
        return jdbc.queryForObject("SELECT code FROM resources WHERE id = ?", String.class, resourceId);
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static List<String> tree(List<Node> nodes) {
        Map<Long, List<Node>> children = new HashMap<>();
        List<Node> roots = new ArrayList<>();
        for (Node node : nodes) {
            if (node.parentId() == null) {
                roots.add(node);
            } else {
                children.computeIfAbsent(node.parentId(), parent -> new ArrayList<>()).add(node);
            }
        }
        List<String> lines = new ArrayList<>();
        roots.forEach(root -> render(root, 0, children, lines));
        return lines;
    }

    private static void render(Node node, int depth, Map<Long, List<Node>> children, List<String> lines) {
        lines.add("  ".repeat(depth) + "[" + node.id() + "] " + node.label());
        children.getOrDefault(node.id(), List.of()).forEach(child -> render(child, depth + 1, children, lines));
    }
}
