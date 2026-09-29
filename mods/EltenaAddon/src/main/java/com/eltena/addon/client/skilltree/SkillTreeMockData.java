package com.eltena.addon.client.skilltree;

import java.util.List;

public final class SkillTreeMockData {
    private SkillTreeMockData() {
    }

    public static String currentJobId() {
        return "swordsman";
    }

    public static String currentJobName() {
        return "\u5263\u58eb";
    }

    public static int availablePoints() {
        return 12;
    }

    public static List<SkillTreeNode> nodes() {
        return List.of(
            new SkillTreeNode(
                "basic_slash",
                "\u57fa\u790e\u65ac\u6483",
                "\u5263\u58eb\u3068\u3057\u3066\u306e\u57fa\u672c\u3068\u306a\u308b\u521d\u6b69\u306e\u65ac\u6483\u3067\u3059\u3002",
                120,
                160,
                1,
                1,
                1,
                SkillNodeState.LEARNED,
                List.of()
            ),
            new SkillTreeNode(
                "guard_stance",
                "\u9632\u5fa1\u69cb\u3048",
                "\u88ab\u30c0\u30e1\u30fc\u30b8\u3092\u6291\u3048\u3064\u3064\u53cd\u6483\u3078\u7e4b\u3052\u308b\u5b88\u308a\u306e\u57fa\u790e\u3067\u3059\u3002",
                380,
                80,
                2,
                0,
                1,
                SkillNodeState.AVAILABLE,
                List.of("basic_slash")
            ),
            new SkillTreeNode(
                "quick_step",
                "\u30af\u30a4\u30c3\u30af\u30b9\u30c6\u30c3\u30d7",
                "\u77ed\u6b69\u3067\u4f4d\u7f6e\u3092\u53d6\u308a\u76f4\u3059\u305f\u3081\u306e\u79fb\u52d5\u652f\u63f4\u6280\u8853\u3067\u3059\u3002",
                380,
                260,
                2,
                1,
                1,
                SkillNodeState.LEARNED,
                List.of()
            ),
            new SkillTreeNode(
                "heavy_slash",
                "\u5f37\u65ac\u6483",
                "\u5927\u304d\u304f\u8e0f\u307f\u8fbc\u3093\u3067\u653e\u3064\u91cd\u3044\u4e00\u6483\u3067\u3059\u3002",
                640,
                160,
                3,
                0,
                3,
                SkillNodeState.AVAILABLE,
                List.of("basic_slash")
            ),
            new SkillTreeNode(
                "blade_focus",
                "\u5203\u96c6\u4e2d",
                "\u5263\u6280\u3092\u4e00\u70b9\u3078\u9ad8\u3081\u3066\u6b21\u306e\u6280\u3078\u7e4b\u3052\u307e\u3059\u3002",
                640,
                340,
                3,
                0,
                2,
                SkillNodeState.AVAILABLE,
                List.of("quick_step")
            ),
            new SkillTreeNode(
                "counter_cut",
                "\u53cd\u6483\u65ac\u308a",
                "\u9632\u5fa1\u306e\u76f4\u5f8c\u304b\u3089\u5207\u308a\u8fd4\u3059\u5fdc\u7528\u30b9\u30ad\u30eb\u3067\u3059\u3002",
                900,
                80,
                4,
                0,
                2,
                SkillNodeState.LOCKED,
                List.of("guard_stance")
            ),
            new SkillTreeNode(
                "sword_aura",
                "\u5263\u6c17",
                "\u5263\u306b\u6c17\u3092\u307e\u3068\u308f\u305b\u3001\u6b21\u306e\u6d3e\u751f\u6280\u306e\u57fa\u790e\u3068\u306a\u308a\u307e\u3059\u3002",
                900,
                210,
                5,
                0,
                1,
                SkillNodeState.LOCKED,
                List.of("heavy_slash")
            ),
            new SkillTreeNode(
                "storm_blade",
                "\u65cb\u98a8\u5263",
                "\u79fb\u52d5\u3068\u65ac\u6483\u3092\u4e00\u4f53\u5316\u3055\u305b\u305f\u4e2d\u7d1a\u5fdc\u7528\u6280\u3067\u3059\u3002",
                1160,
                340,
                5,
                0,
                1,
                SkillNodeState.LOCKED,
                List.of("blade_focus")
            ),
            new SkillTreeNode(
                "sword_mastery",
                "\u5263\u8853\u719f\u9054",
                "\u5263\u58eb\u30eb\u30fc\u30c8\u306e\u96c6\u5927\u6210\u3068\u306a\u308b\u7d42\u76e4\u6280\u80fd\u3067\u3059\u3002",
                1440,
                230,
                8,
                0,
                1,
                SkillNodeState.LOCKED,
                List.of("sword_aura", "storm_blade")
            )
        );
    }

    public static List<SkillTreeEdge> edges() {
        return List.of(
            new SkillTreeEdge("basic_slash", "heavy_slash"),
            new SkillTreeEdge("basic_slash", "guard_stance"),
            new SkillTreeEdge("guard_stance", "counter_cut"),
            new SkillTreeEdge("quick_step", "blade_focus"),
            new SkillTreeEdge("heavy_slash", "sword_aura"),
            new SkillTreeEdge("blade_focus", "storm_blade"),
            new SkillTreeEdge("sword_aura", "sword_mastery"),
            new SkillTreeEdge("storm_blade", "sword_mastery")
        );
    }
}
