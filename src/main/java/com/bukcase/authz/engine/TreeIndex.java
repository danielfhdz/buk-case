package com.bukcase.authz.engine;

import com.bukcase.authz.api.ResourceCatalog;
import com.bukcase.authz.cache.AuthzVersion;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * In-memory copy of the area and resource trees (parent pointers only), reloaded when the
 * authorization version changes. Both trees are small compared to the data they classify
 * (hundreds to a few thousand nodes), so walking parents is cheaper than any round trip.
 */
@Component
public class TreeIndex implements ResourceCatalog {

    private final JdbcTemplate jdbc;
    private final AuthzVersion version;
    private volatile Snapshot snapshot;

    public TreeIndex(JdbcTemplate jdbc, AuthzVersion version) {
        this.jdbc = jdbc;
        this.version = version;
    }

    /**
     * The area itself plus all its ancestors. A record without an area is treated as belonging to
     * the root, so only company-wide grants cover it.
     */
    public Set<Long> areaPath(Long areaId) {
        Snapshot current = current();
        return pathToRoot(areaId == null ? current.rootAreaId() : areaId, current.areaParents());
    }

    public Set<Long> resourcePath(long resourceId) {
        return pathToRoot(resourceId, current().resourceParents());
    }

    public boolean isResourceAncestorOrSelf(long candidateAncestor, long resourceId) {
        return resourcePath(resourceId).contains(candidateAncestor);
    }

    public long rootAreaId() {
        return current().rootAreaId();
    }

    public String resourceCodeOf(long resourceId) {
        String code = current().resourceCodes().get(resourceId);
        return code == null ? "UNKNOWN" : code;
    }

    @Override
    public long idOf(String resourceCode) {
        Long id = current().resourceIds().get(resourceCode);
        if (id == null) {
            throw new IllegalArgumentException("Unknown resource code: " + resourceCode);
        }
        return id;
    }

    private Snapshot current() {
        long currentVersion = version.current();
        Snapshot loaded = snapshot;
        if (loaded == null || loaded.version() != currentVersion) {
            loaded = load(currentVersion);
            snapshot = loaded;
        }
        return loaded;
    }

    private Snapshot load(long currentVersion) {
        Map<Long, Long> areaParents = new HashMap<>();
        long[] root = {-1L};
        jdbc.query("SELECT id, parent_id FROM areas", rs -> {
            long id = rs.getLong("id");
            long parent = rs.getLong("parent_id");
            if (rs.wasNull()) {
                areaParents.put(id, null);
                root[0] = id;
            } else {
                areaParents.put(id, parent);
            }
        });

        Map<Long, Long> resourceParents = new HashMap<>();
        Map<String, Long> resourceIds = new HashMap<>();
        Map<Long, String> resourceCodes = new HashMap<>();
        jdbc.query("SELECT id, code, parent_id FROM resources", rs -> {
            long id = rs.getLong("id");
            long parent = rs.getLong("parent_id");
            resourceParents.put(id, rs.wasNull() ? null : parent);
            resourceIds.put(rs.getString("code"), id);
            resourceCodes.put(id, rs.getString("code"));
        });

        return new Snapshot(currentVersion, areaParents, root[0], resourceParents, resourceIds, resourceCodes);
    }

    private static Set<Long> pathToRoot(long start, Map<Long, Long> parents) {
        Set<Long> path = new LinkedHashSet<>();
        Long node = start;
        while (node != null && path.add(node)) {
            node = parents.get(node);
        }
        return path;
    }

    private record Snapshot(long version,
                            Map<Long, Long> areaParents,
                            long rootAreaId,
                            Map<Long, Long> resourceParents,
                            Map<String, Long> resourceIds,
                            Map<Long, String> resourceCodes) {
    }
}
