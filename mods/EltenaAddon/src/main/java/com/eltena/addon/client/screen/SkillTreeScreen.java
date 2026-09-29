package com.eltena.addon.client.screen;

import com.eltena.addon.client.hud.EltenaNotificationOverlay;
import com.eltena.addon.client.skilltree.SkillNodeState;
import com.eltena.addon.client.skilltree.SkillTreeEdge;
import com.eltena.addon.client.skilltree.SkillTreeMockData;
import com.eltena.addon.client.skilltree.SkillTreeNode;
import com.eltena.addon.client.skilltree.SkillTreeViewState;
import com.eltena.addon.client.ui.AddonUiFont;
import com.eltena.addon.network.EltenaAddonNetwork;
import com.eltena.addon.network.ModSyncClient;
import com.eltena.addon.network.model.SkillNodePayload;
import com.eltena.addon.network.model.SkillTreePayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

public final class SkillTreeScreen extends Screen {
    private static final int SAFE_MARGIN = 16;
    private static final int ICON_SIZE = 18;
    private static final int NODE_WIDTH = 238;
    private static final int NODE_HEIGHT = 84;
    private static final int NODE_HORIZONTAL_PADDING = 14;
    private static final int NODE_VERTICAL_PADDING = 10;
    private static final int NODE_TEXT_START = 38;
    private static final int NODE_TEXT_WIDTH = NODE_WIDTH - NODE_TEXT_START - NODE_HORIZONTAL_PADDING;
    private static final int COLUMN_GAP = 116;
    private static final int ROW_GAP = 42;
    private static final int GRAPH_PADDING_X = 44;
    private static final int GRAPH_PADDING_Y = 34;
    private static final int EDGE_GAP = 12;
    private static final int EDGE_PORT_STEP = 8;
    private static final int EDGE_PORT_INSET = 16;
    private static final int EDGE_LANE_PADDING = 10;
    private static final int EDGE_CORRIDOR_GAP = 18;
    private static final float INITIAL_ZOOM = 0.82F;
    private static final float HEADER_TITLE_SCALE = 1.16F;
    private static final float HEADER_BODY_SCALE = 1.03F;
    private static final float NODE_TITLE_SCALE = 1.04F;
    private static final float NODE_BODY_SCALE = 0.94F;
    private static final float NODE_STATE_SCALE = 0.94F;
    private static final float FOOTER_SCALE = 1.00F;

    private final List<SkillTreeNode> nodes = new ArrayList<>();
    private final List<SkillTreeEdge> edges = new ArrayList<>();
    private final Map<String, SkillTreeNode> nodeById = new HashMap<>();
    private final Map<String, NodePlacement> nodePlacements = new HashMap<>();
    private final Map<String, Integer> nodeColumns = new HashMap<>();
    private final Map<String, String> nodeEffects = new HashMap<>();
    private final Map<String, Integer> nodeRequiredLevels = new HashMap<>();
    private final Map<String, List<String>> nodeUnlockAbilities = new HashMap<>();
    private final SkillTreeViewState viewState = new SkillTreeViewState(72.0D, 54.0D, INITIAL_ZOOM);

    private String currentJobName = SkillTreeMockData.currentJobName();
    private int availablePoints = SkillTreeMockData.availablePoints();
    private boolean draggingCanvas;
    private boolean payloadBacked;
    private boolean viewInitialized;
    private LayoutMetrics layout = LayoutMetrics.defaultLayout();
    private GraphBounds graphBounds = GraphBounds.empty();

    public SkillTreeScreen() {
        super(AddonUiFont.translatable("screen.eltenaaddon.skill_tree"));
        reloadSyncContext();
    }

    public void refreshFromSync() {
        reloadSyncContext();
        rebuildGraphLayout(false);
    }

    @Override
    protected void init() {
        recalculateLayout();
        reloadSyncContext();
        rebuildGraphLayout(true);
        this.clearWidgets();
        this.addRenderableWidget(
            Button.builder(AddonUiFont.translatable("screen.eltenaaddon.back_to_menu"), button -> openMenu())
                .bounds(rootLeft() + 8, rootTop() + 8, 104, 20)
                .build()
        );
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        recalculateLayout();
        reloadSyncContext();
        rebuildGraphLayout(true);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        recalculateLayout();
        float fitScale = fitScale();
        int virtualMouseX = toVirtualX(mouseX);
        int virtualMouseY = toVirtualY(mouseY);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(fitScale, fitScale, 1.0F);
        SkillTreeNode hovered = hoveredNode(virtualMouseX, virtualMouseY);
        renderFrame(guiGraphics);
        renderTopBar(guiGraphics);
        renderCanvas(guiGraphics, hovered);
        renderFooter(guiGraphics);
        super.render(guiGraphics, virtualMouseX, virtualMouseY, partialTick);
        renderHoverTooltip(guiGraphics, hovered, virtualMouseX, virtualMouseY);
        guiGraphics.pose().popPose();
        EltenaNotificationOverlay.renderOnScreen(guiGraphics);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics) {
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
    }

    @Override
    public void renderTransparentBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !hasShiftDown()) {
            openMenu();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double virtualMouseX = toVirtualX(mouseX);
        double virtualMouseY = toVirtualY(mouseY);
        if (super.mouseClicked(virtualMouseX, virtualMouseY, button)) {
            return true;
        }
        SkillTreeNode hovered = hoveredNode(virtualMouseX, virtualMouseY);
        if (hovered != null && button == 0) {
            playNodeSound(hovered.state());
            EltenaAddonNetwork.sendSkillLearnRequest(hovered.id());
            return true;
        }
        if (isInsideCanvas(virtualMouseX, virtualMouseY) && (button == 0 || button == 1)) {
            draggingCanvas = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingCanvas = false;
        return super.mouseReleased(toVirtualX(mouseX), toVirtualY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingCanvas) {
            viewState.pan(dragX / fitScale(), dragY / fitScale());
            return true;
        }
        return super.mouseDragged(toVirtualX(mouseX), toVirtualY(mouseY), button, dragX / fitScale(), dragY / fitScale());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        double virtualMouseX = toVirtualX(mouseX);
        double virtualMouseY = toVirtualY(mouseY);
        if (!isInsideCanvas(virtualMouseX, virtualMouseY)) {
            return super.mouseScrolled(virtualMouseX, virtualMouseY, deltaX, deltaY);
        }
        if (hasControlDown()) {
            viewState.adjustZoom(deltaY * 0.08D);
            return true;
        }
        viewState.pan(deltaX * 28.0D, deltaY * 24.0D);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderFrame(GuiGraphics guiGraphics) {
        int panelLeft = rootLeft();
        int panelTop = rootTop();
        int panelRight = rootRight();
        int panelBottom = rootBottom();
        guiGraphics.fill(0, 0, virtualScreenWidth(), virtualScreenHeight(), 0xA4060C14);
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xE10A1522);
        guiGraphics.fill(panelLeft + 2, panelTop + 2, panelRight - 2, panelBottom - 2, 0x7A112237);
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + headerHeight(), 0xE7132235);
        guiGraphics.fill(panelLeft, panelBottom - footerHeight(), panelRight, panelBottom, 0xD80D1723);
        drawBorder(guiGraphics, panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 0xFF2F5D87);
        guiGraphics.fill(panelLeft + 12, panelTop + headerHeight() - 2, panelRight - 12, panelTop + headerHeight(), 0x664D8BC0);
    }

    private void renderTopBar(GuiGraphics guiGraphics) {
        int left = rootLeft() + 126;
        int top = rootTop() + 10;
        int right = rootRight() - safeMargin();
        String syncError = shortSyncError();

        drawScaledString(guiGraphics, AddonUiFont.apply(this.title), left, top + 2, 0xFFFFFFFF, HEADER_TITLE_SCALE);
        drawScaledString(guiGraphics, AddonUiFont.translatable("screen.eltenaaddon.skill_tree.summary.points"), left, top + 24, 0xFFA9C8E8, HEADER_BODY_SCALE);
        drawScaledString(guiGraphics, AddonUiFont.text(String.valueOf(availablePoints)), left + 90, top + 24, 0xFFF4D06F, HEADER_BODY_SCALE);
        drawScaledString(guiGraphics, AddonUiFont.translatable("screen.eltenaaddon.skill_tree.summary.job"), left + 136, top + 24, 0xFFA9C8E8, HEADER_BODY_SCALE);
        drawScaledString(guiGraphics, AddonUiFont.text(currentJobName), left + 214, top + 24, 0xFF9FD7FF, HEADER_BODY_SCALE);
        drawScaledString(guiGraphics, AddonUiFont.translatable("screen.eltenaaddon.sync.label"), right - 220, top + 24, 0xFFA9C8E8, HEADER_BODY_SCALE);
        drawScaledString(guiGraphics, AddonUiFont.text(skillTreeSyncStatusText()), right - 140, top + 24, 0xFFC7D2DF, HEADER_BODY_SCALE);
        if (!syncError.isBlank()) {
            drawScaledString(guiGraphics, AddonUiFont.translatable("screen.eltenaaddon.sync.error_short", syncError), right - 220, top + 40, 0xFFF4D06F, HEADER_BODY_SCALE);
        }
    }

    private void renderCanvas(GuiGraphics guiGraphics, SkillTreeNode hovered) {
        int left = canvasLeft();
        int top = canvasTop();
        int right = canvasRight();
        int bottom = canvasBottom();
        AddonScreenLayout.ScissorRect scissor = AddonScreenLayout.toScissor(this, left, top, right - left, bottom - top);
        guiGraphics.enableScissor(scissor.left(), scissor.top(), scissor.right(), scissor.bottom());
        renderCanvasBackdrop(guiGraphics, left, top, right, bottom);
        renderEdges(guiGraphics);
        for (SkillTreeNode node : nodes) {
            renderNode(guiGraphics, node, hovered != null && hovered.id().equals(node.id()));
        }
        guiGraphics.disableScissor();
    }

    private void renderCanvasBackdrop(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
        guiGraphics.fill(left, top, right, bottom, 0xF709121B);
        guiGraphics.fill(left + 80, top + 40, right - 80, bottom - 40, 0x120E2337);
        guiGraphics.fill(left + 180, top + 90, right - 180, bottom - 90, 0x120F2E47);
        for (int x = left; x < right; x += 64) {
            guiGraphics.vLine(x, top, bottom, 0x182B4258);
        }
        for (int y = top; y < bottom; y += 64) {
            guiGraphics.hLine(left, right, y, 0x182B4258);
        }
    }

    private void renderEdges(GuiGraphics guiGraphics) {
        for (SkillTreeEdge edge : edges) {
            SkillTreeNode from = nodeById.get(edge.fromNodeId());
            SkillTreeNode to = nodeById.get(edge.toNodeId());
            if (from == null || to == null) {
                continue;
            }
            NodeBounds fromBounds = nodeBounds(from);
            NodeBounds toBounds = nodeBounds(to);
            int portOffset = siblingLaneOffset(edge.toNodeId(), edge.fromNodeId());
            int fromY = portY(fromBounds, portOffset);
            int toY = portY(toBounds, portOffset);
            int startEdgeX = fromBounds.x() + fromBounds.width();
            int endEdgeX = toBounds.x();
            int startX = startEdgeX + EDGE_GAP;
            int endX = endEdgeX - EDGE_GAP;
            int parentColumn = nodeColumns.getOrDefault(from.id(), 0);
            int childColumn = nodeColumns.getOrDefault(to.id(), parentColumn + 1);
            if (childColumn - parentColumn <= 2) {
                int laneX = TreeEdgeRenderer.clampLaneX(startX, endX, (startX + endX) / 2 + portOffset * 3, EDGE_LANE_PADDING);
                TreeEdgeRenderer.renderPolyline(guiGraphics, edgeColor(from, to),
                    startEdgeX, fromY,
                    startX, fromY,
                    laneX, fromY,
                    laneX, toY,
                    endX, toY,
                    endEdgeX, toY
                );
                continue;
            }
            int corridorY = corridorY(fromBounds, toBounds, Math.abs(portOffset), parentColumn, childColumn);
            TreeEdgeRenderer.renderPolyline(guiGraphics, edgeColor(from, to),
                startEdgeX, fromY,
                startX, fromY,
                startX, corridorY,
                endX, corridorY,
                endX, toY,
                endEdgeX, toY
            );
        }
    }

    private void renderNode(GuiGraphics guiGraphics, SkillTreeNode node, boolean hovered) {
        int x = nodeScreenX(node);
        int y = nodeScreenY(node);
        float zoom = (float) viewState.zoom();
        int fillColor = nodeFillColor(node.state());
        int borderColor = hovered ? hoverBorderColor(node.state()) : nodeBorderColor(node.state());

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(zoom, zoom, 1.0F);
        guiGraphics.fill(2, 2, NODE_WIDTH + 4, NODE_HEIGHT + 4, hovered ? 0x443AA8FF : 0x22000000);
        guiGraphics.fill(0, 0, NODE_WIDTH, NODE_HEIGHT, fillColor);
        guiGraphics.fill(4, 4, NODE_WIDTH - 4, NODE_HEIGHT - 4, innerCardColor(node.state()));
        drawBorder(guiGraphics, 0, 0, NODE_WIDTH, NODE_HEIGHT, borderColor);
        if (hovered) {
            drawBorder(guiGraphics, -1, -1, NODE_WIDTH + 2, NODE_HEIGHT + 2, hoverGlowColor(node.state()));
        }

        int iconY = NODE_VERTICAL_PADDING + 1;
        guiGraphics.fill(10, iconY, 10 + ICON_SIZE, iconY + ICON_SIZE, iconInnerColor(node.state()));
        drawBorder(guiGraphics, 9, iconY - 1, ICON_SIZE + 2, ICON_SIZE + 2, iconBorderColor(node.state()));
        guiGraphics.drawCenteredString(this.font, AddonUiFont.text(node.name().isBlank() ? "?" : node.name().substring(0, 1)), 18, iconY + 4, 0xFFFFFFFF);

        int drawY = NODE_VERTICAL_PADDING;
        for (FormattedCharSequence line : fitTextLines(node.name(), NODE_TITLE_SCALE, NODE_TEXT_WIDTH, 2)) {
            drawScaledFormattedString(guiGraphics, line, NODE_TEXT_START, drawY, titleColor(node.state()), NODE_TITLE_SCALE);
            drawY += 12;
        }
        drawY += 3;
        for (String detailLine : nodeDetailLines(node)) {
            for (FormattedCharSequence line : fitTextLines(detailLine, NODE_BODY_SCALE, NODE_TEXT_WIDTH, 1)) {
                drawScaledFormattedString(guiGraphics, line, NODE_TEXT_START, drawY, 0xFFDDE6F2, NODE_BODY_SCALE);
                drawY += 10;
            }
        }
        drawScaledString(guiGraphics, AddonUiFont.text(stateLabel(node.state())), NODE_HORIZONTAL_PADDING, NODE_HEIGHT - 18, stateTextColor(node.state()), NODE_STATE_SCALE);
        guiGraphics.pose().popPose();
    }

    private void renderFooter(GuiGraphics guiGraphics) {
        int left = rootLeft() + 14;
        int top = rootBottom() - footerHeight() + 5;
        int width = rootRight() - rootLeft() - 28;
        guiGraphics.fill(left, top, left + width, top + 20, 0xC30F1824);
        drawBorder(guiGraphics, left, top, width, 20, 0x664B7CA2);
        drawKeyHint(guiGraphics, left + 10, top + 4, "screen.eltenaaddon.tree.key.drag", "screen.eltenaaddon.tree.help.pan");
        drawKeyHint(guiGraphics, left + 176, top + 4, "screen.eltenaaddon.tree.key.scroll", "screen.eltenaaddon.tree.help.pan");
        drawKeyHint(guiGraphics, left + 322, top + 4, "screen.eltenaaddon.tree.key.zoom", "screen.eltenaaddon.tree.help.zoom");
        drawKeyHint(guiGraphics, Math.min(left + width - 148, left + 558), top + 4, "screen.eltenaaddon.tree.key.esc", "screen.eltenaaddon.tree.help.back");
    }

    private void renderHoverTooltip(GuiGraphics guiGraphics, SkillTreeNode hovered, int mouseX, int mouseY) {
        if (hovered == null || !isInsideCanvas(mouseX, mouseY)) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(AddonUiFont.text(hovered.name()));
        int requiredLevel = nodeRequiredLevels.getOrDefault(hovered.id(), 0);
        if (requiredLevel > 0) {
            tooltip.add(AddonUiFont.translatable("screen.eltenaaddon.skill_tree.tooltip.required_level", requiredLevel));
        }
        tooltip.add(AddonUiFont.translatable("screen.eltenaaddon.skill_tree.tooltip.cost", hovered.requiredPoints()));
        if (!hovered.prerequisites().isEmpty()) {
            tooltip.add(AddonUiFont.translatable("screen.eltenaaddon.skill_tree.tooltip.prerequisites", formatPrerequisites(hovered)));
        }
        List<String> unlockAbilities = nodeUnlockAbilities.getOrDefault(hovered.id(), List.of());
        if (!unlockAbilities.isEmpty()) {
            tooltip.add(AddonUiFont.translatable(
                "screen.eltenaaddon.skill_tree.tooltip.unlock_ability",
                String.join(" / ", unlockAbilities)
            ));
            tooltip.add(AddonUiFont.translatable("screen.eltenaaddon.skill_tree.tooltip.assignment"));
        } else {
            String effects = nodeEffects.getOrDefault(hovered.id(), "");
            if (!effects.isBlank() && !effects.equals(AddonUiFont.translatable("screen.eltenaaddon.skill_tree.effects.none").getString())) {
                tooltip.add(AddonUiFont.translatable("screen.eltenaaddon.skill_tree.tooltip.effects", effects));
            }
        }
        tooltip.add(AddonUiFont.translatable("screen.eltenaaddon.skill_tree.tooltip.state", stateLabel(hovered.state())));
        renderCustomTooltip(guiGraphics, tooltip, mouseX, mouseY);
    }

    private void reloadSyncContext() {
        SkillTreePayload payload = ModSyncClient.getSkillTreeState();
        payloadBacked = payload != null && payload.nodes() != null && !payload.nodes().isEmpty();
        currentJobName = payload != null && payload.currentJobName() != null && !payload.currentJobName().isBlank()
            ? payload.currentJobName()
            : SkillTreeMockData.currentJobName();
        availablePoints = payload != null ? Math.max(0, payload.skillPoint()) : SkillTreeMockData.availablePoints();

        nodes.clear();
        edges.clear();
        nodeById.clear();
        nodePlacements.clear();
        nodeColumns.clear();
        nodeEffects.clear();
        nodeRequiredLevels.clear();
        nodeUnlockAbilities.clear();

        if (!payloadBacked) {
            nodes.addAll(SkillTreeMockData.nodes());
            edges.addAll(SkillTreeMockData.edges());
            for (SkillTreeNode node : nodes) {
                nodeById.put(node.id(), node);
                nodeEffects.put(node.id(), AddonUiFont.translatable("screen.eltenaaddon.skill_tree.effects.none").getString());
                nodeRequiredLevels.put(node.id(), 0);
                nodeUnlockAbilities.put(node.id(), List.of());
            }
            return;
        }

        for (int index = 0; index < payload.nodes().size(); index++) {
            SkillNodePayload nodePayload = payload.nodes().get(index);
            SkillTreeNode fallback = findMockNode(nodePayload.id());
            int canvasX = nodePayload.positionX() != 0 ? nodePayload.positionX() : fallback != null ? fallback.canvasX() : 120 + (index / 4) * 340;
            int canvasY = nodePayload.positionY() != 0 ? nodePayload.positionY() : fallback != null ? fallback.canvasY() : 120 + (index % 4) * 170;
            String displayName = nodePayload.displayName() != null && !nodePayload.displayName().isBlank()
                ? nodePayload.displayName()
                : fallback != null ? fallback.name() : nodePayload.id();
            String description = TreeDisplayFormatter.formatSkillDescription(nodePayload, fallback != null ? fallback.description() : "");
            List<String> prerequisites = !nodePayload.requiresDisplay().isEmpty() ? nodePayload.requiresDisplay() : nodePayload.prerequisites();
            SkillTreeNode node = new SkillTreeNode(
                nodePayload.id(),
                displayName,
                description,
                canvasX,
                canvasY,
                nodePayload.requiredPoints(),
                nodePayload.currentRank(),
                Math.max(1, nodePayload.maxRank()),
                mapState(nodePayload),
                prerequisites
            );
            nodes.add(node);
            nodeById.put(node.id(), node);
            nodeEffects.put(node.id(), TreeDisplayFormatter.formatSkillEffects(nodePayload.effectsDisplay()));
            nodeRequiredLevels.put(node.id(), Math.max(0, nodePayload.requiredLevel()));
            nodeUnlockAbilities.put(node.id(), List.copyOf(nodePayload.unlockAbilitiesDisplay()));
            for (String prerequisite : nodePayload.prerequisites()) {
                edges.add(new SkillTreeEdge(prerequisite, nodePayload.id()));
            }
        }
    }

    private void rebuildGraphLayout(boolean resetView) {
        nodePlacements.clear();
        nodeColumns.clear();
        if (nodes.isEmpty()) {
            graphBounds = GraphBounds.empty();
            if (resetView) {
                viewState.set(72.0D, 54.0D, INITIAL_ZOOM);
            }
            return;
        }

        Map<String, Integer> depthByNode = new HashMap<>();
        for (SkillTreeNode node : nodes) {
            resolveDepth(node, depthByNode);
        }
        TreeMap<Integer, List<SkillTreeNode>> columns = new TreeMap<>();
        for (SkillTreeNode node : nodes) {
            columns.computeIfAbsent(depthByNode.getOrDefault(node.id(), 0), unused -> new ArrayList<>()).add(node);
        }

        int currentX = GRAPH_PADDING_X;
        for (Map.Entry<Integer, List<SkillTreeNode>> entry : columns.entrySet()) {
            int columnIndex = entry.getKey();
            List<SkillTreeNode> columnNodes = entry.getValue();
            columnNodes.sort(Comparator
                .comparingDouble((SkillTreeNode node) -> preferredAnchorY(node))
                .thenComparingInt(SkillTreeNode::canvasY)
                .thenComparing(SkillTreeNode::id));
            int columnHeight = columnNodes.size() * NODE_HEIGHT + Math.max(0, columnNodes.size() - 1) * ROW_GAP;
            int currentY = GRAPH_PADDING_Y + Math.max(0, (Math.max(canvasBottom() - canvasTop() - GRAPH_PADDING_Y * 2, columnHeight) - columnHeight) / 2);
            for (SkillTreeNode node : columnNodes) {
                nodePlacements.put(node.id(), new NodePlacement(currentX, currentY));
                nodeColumns.put(node.id(), columnIndex);
                currentY += NODE_HEIGHT + ROW_GAP;
            }
            currentX += NODE_WIDTH + COLUMN_GAP;
        }

        graphBounds = computeGraphBounds();
        if (resetView || !viewInitialized) {
            centerGraphInView();
            viewInitialized = true;
        }
    }

    private int resolveDepth(SkillTreeNode node, Map<String, Integer> depthByNode) {
        Integer cached = depthByNode.get(node.id());
        if (cached != null) {
            return cached;
        }
        int depth = 0;
        for (String prerequisiteId : prerequisiteIds(node.id())) {
            SkillTreeNode prerequisite = nodeById.get(prerequisiteId);
            if (prerequisite != null) {
                depth = Math.max(depth, resolveDepth(prerequisite, depthByNode) + 1);
            }
        }
        depthByNode.put(node.id(), depth);
        return depth;
    }

    private List<String> prerequisiteIds(String targetNodeId) {
        List<String> prerequisiteIds = new ArrayList<>();
        for (SkillTreeEdge edge : edges) {
            if (edge.toNodeId().equals(targetNodeId)) {
                prerequisiteIds.add(edge.fromNodeId());
            }
        }
        return prerequisiteIds;
    }

    private int siblingLaneOffset(String targetNodeId, String prerequisiteId) {
        List<String> prerequisites = prerequisiteIds(targetNodeId);
        int index = prerequisites.indexOf(prerequisiteId);
        if (index < 0) {
            return 0;
        }
        return (index - (prerequisites.size() - 1) / 2) * EDGE_PORT_STEP;
    }

    private int portY(NodeBounds bounds, int portOffset) {
        int minY = bounds.y() + EDGE_PORT_INSET;
        int maxY = bounds.y() + bounds.height() - EDGE_PORT_INSET;
        int desiredY = bounds.y() + bounds.height() / 2 + portOffset;
        return Math.max(minY, Math.min(maxY, desiredY));
    }

    private int corridorY(NodeBounds fromBounds, NodeBounds toBounds, int portOffset, int parentColumn, int childColumn) {
        int localTop = Math.min(fromBounds.y(), toBounds.y());
        int desiredY = localTop - EDGE_CORRIDOR_GAP - portOffset - Math.max(0, childColumn - parentColumn - 2) * 6;
        return Math.max(canvasTop() + 10, desiredY);
    }

    private double preferredAnchorY(SkillTreeNode node) {
        List<String> prerequisites = prerequisiteIds(node.id());
        if (prerequisites.isEmpty()) {
            return node.canvasY();
        }
        double total = 0.0D;
        int counted = 0;
        for (String prerequisiteId : prerequisites) {
            NodePlacement placement = nodePlacements.get(prerequisiteId);
            if (placement != null) {
                total += placement.y() + NODE_HEIGHT / 2.0D;
                counted++;
            }
        }
        return counted == 0 ? node.canvasY() : total / counted;
    }

    private GraphBounds computeGraphBounds() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (NodePlacement placement : nodePlacements.values()) {
            minX = Math.min(minX, placement.x());
            minY = Math.min(minY, placement.y());
            maxX = Math.max(maxX, placement.x() + NODE_WIDTH);
            maxY = Math.max(maxY, placement.y() + NODE_HEIGHT);
        }
        if (minX == Integer.MAX_VALUE) {
            return GraphBounds.empty();
        }
        return new GraphBounds(minX, minY, maxX, maxY);
    }

    private void centerGraphInView() {
        double graphWidth = Math.max(1, graphBounds.width());
        double graphHeight = Math.max(1, graphBounds.height());
        double visibleWidth = canvasRight() - canvasLeft();
        double visibleHeight = canvasBottom() - canvasTop();
        double offsetX = (visibleWidth - graphWidth * INITIAL_ZOOM) / 2.0D - graphBounds.minX() * INITIAL_ZOOM;
        double offsetY = Math.max(18.0D, (visibleHeight - graphHeight * INITIAL_ZOOM) / 2.0D) - graphBounds.minY() * INITIAL_ZOOM;
        viewState.set(offsetX / INITIAL_ZOOM, offsetY / INITIAL_ZOOM, INITIAL_ZOOM);
    }

    private SkillTreeNode findMockNode(String id) {
        for (SkillTreeNode node : SkillTreeMockData.nodes()) {
            if (node.id().equals(id)) {
                return node;
            }
        }
        return null;
    }

    private static SkillNodeState mapState(SkillNodePayload payload) {
        if (payload.currentRank() > 0 || "learned".equalsIgnoreCase(payload.state())) {
            return SkillNodeState.LEARNED;
        }
        if ("available".equalsIgnoreCase(payload.state())) {
            return SkillNodeState.AVAILABLE;
        }
        return SkillNodeState.LOCKED;
    }

    private SkillTreeNode hoveredNode(double mouseX, double mouseY) {
        if (!isInsideCanvas(mouseX, mouseY)) {
            return null;
        }
        for (SkillTreeNode node : nodes) {
            NodeBounds bounds = nodeBounds(node);
            if (mouseX >= bounds.x() && mouseX <= bounds.x() + bounds.width() && mouseY >= bounds.y() && mouseY <= bounds.y() + bounds.height()) {
                return node;
            }
        }
        return null;
    }

    private boolean isInsideCanvas(double mouseX, double mouseY) {
        return mouseX >= canvasLeft() && mouseX <= canvasRight() && mouseY >= canvasTop() && mouseY <= canvasBottom();
    }

    private int canvasLeft() {
        return layout.canvasLeft();
    }

    private int canvasTop() {
        return layout.canvasTop();
    }

    private int canvasRight() {
        return layout.canvasRight();
    }

    private int canvasBottom() {
        return layout.canvasBottom();
    }

    private int headerHeight() {
        return AddonScreenLayout.headerHeight();
    }

    private int footerHeight() {
        return AddonScreenLayout.footerHeight();
    }

    private int safeMargin() {
        return this.rootWidth() < 900 ? 12 : SAFE_MARGIN;
    }

    private int rootWidth() {
        return layout.rootWidth();
    }

    private int rootLeft() {
        return layout.rootLeft();
    }

    private int rootTop() {
        return layout.rootTop();
    }

    private int rootRight() {
        return layout.rootRight();
    }

    private int rootBottom() {
        return layout.rootBottom();
    }

    private float fitScale() {
        return AddonScreenLayout.fitScale(this);
    }

    private int virtualScreenWidth() {
        return AddonScreenLayout.virtualScreenWidth(this);
    }

    private int virtualScreenHeight() {
        return AddonScreenLayout.virtualScreenHeight(this);
    }

    private int toVirtualX(double mouseX) {
        return AddonScreenLayout.toVirtualX(this, mouseX);
    }

    private int toVirtualY(double mouseY) {
        return AddonScreenLayout.toVirtualY(this, mouseY);
    }

    private void recalculateLayout() {
        int rootLeft = AddonScreenLayout.rootLeft(this);
        int rootTop = AddonScreenLayout.rootTop(this);
        int canvasLeft = rootLeft + safeMargin();
        int canvasTop = AddonScreenLayout.contentTop(this) + 24;
        int canvasRight = rootLeft + AddonScreenLayout.ROOT_WIDTH - safeMargin();
        int canvasBottom = AddonScreenLayout.contentBottom(this) - 8;
        layout = new LayoutMetrics(rootLeft, rootTop, AddonScreenLayout.ROOT_WIDTH, AddonScreenLayout.ROOT_HEIGHT, canvasLeft, canvasTop, canvasRight, Math.max(canvasTop + 60, canvasBottom));
    }

    private int nodeScreenX(SkillTreeNode node) {
        NodePlacement placement = nodePlacements.get(node.id());
        int canvasX = placement != null ? placement.x() : node.canvasX();
        return (int) Math.round(canvasLeft() + viewState.offsetX() + canvasX * viewState.zoom());
    }

    private int nodeScreenY(SkillTreeNode node) {
        NodePlacement placement = nodePlacements.get(node.id());
        int canvasY = placement != null ? placement.y() : node.canvasY();
        return (int) Math.round(canvasTop() + viewState.offsetY() + canvasY * viewState.zoom());
    }

    private NodeBounds nodeBounds(SkillTreeNode node) {
        return new NodeBounds(nodeScreenX(node), nodeScreenY(node), Math.round(NODE_WIDTH * (float) viewState.zoom()), Math.round(NODE_HEIGHT * (float) viewState.zoom()));
    }

    private List<FormattedCharSequence> fitTextLines(String raw, float scale, int maxWidth, int maxLines) {
        int virtualWidth = Math.max(32, Math.round(maxWidth / scale));
        List<FormattedCharSequence> lines = this.font.split(AddonUiFont.text(raw), virtualWidth);
        if (lines.size() <= maxLines) {
            return lines;
        }
        List<FormattedCharSequence> clipped = new ArrayList<>(lines.subList(0, Math.max(0, maxLines - 1)));
        String ellipsis = this.font.plainSubstrByWidth(raw, Math.max(24, virtualWidth - this.font.width("..."))) + "...";
        clipped.add(AddonUiFont.text(ellipsis).getVisualOrderText());
        return clipped;
    }

    private void renderCustomTooltip(GuiGraphics guiGraphics, List<Component> tooltip, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = tooltip.stream().map(Component::getVisualOrderText).toList();
        int width = 0;
        for (FormattedCharSequence line : lines) {
            width = Math.max(width, this.font.width(line));
        }
        int lineHeight = 12;
        int tooltipWidth = width + 14;
        int tooltipHeight = lines.size() * lineHeight + 10;
        int x = mouseX + 16;
        int y = mouseY + 16;
        if (x + tooltipWidth > virtualScreenWidth() - 8) {
            x = mouseX - tooltipWidth - 16;
        }
        if (y + tooltipHeight > virtualScreenHeight() - 8) {
            y = mouseY - tooltipHeight - 16;
        }
        x = Math.max(8, x);
        y = Math.max(8, y);

        guiGraphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, 0xEE0E1A28);
        guiGraphics.fill(x + 2, y + 2, x + tooltipWidth - 2, y + 22, 0x332D5886);
        drawBorder(guiGraphics, x, y, tooltipWidth, tooltipHeight, 0xFF3E709D);

        int drawY = y + 6;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFF6D77B : 0xFFF1F5F9;
            guiGraphics.drawString(this.font, lines.get(i), x + 7, drawY, color, false);
            drawY += lineHeight;
        }
    }

    private void drawKeyHint(GuiGraphics guiGraphics, int x, int y, String keyLabelKey, String descriptionKey) {
        Component keyLabel = AddonUiFont.translatable(keyLabelKey);
        int pillWidth = Math.max(54, Math.round(this.font.width(keyLabel) * FOOTER_SCALE) + 14);
        guiGraphics.fill(x, y, x + pillWidth, y + 16, 0xC8142232);
        drawBorder(guiGraphics, x, y, pillWidth, 16, 0x884C7AA1);
        drawScaledCenteredString(guiGraphics, keyLabel, x + pillWidth / 2, y + 3, 0xFFF2F7FD, FOOTER_SCALE);
        drawScaledString(guiGraphics, AddonUiFont.translatable(descriptionKey), x + pillWidth + 8, y + 2, 0xFFD5E7FA, FOOTER_SCALE);
    }

    private void drawScaledString(GuiGraphics guiGraphics, Component text, int x, int y, int color, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(this.font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private void drawScaledFormattedString(GuiGraphics guiGraphics, FormattedCharSequence text, int x, int y, int color, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(this.font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private void drawScaledCenteredString(GuiGraphics guiGraphics, Component text, int centerX, int y, int color, float scale) {
        int width = Math.round(this.font.width(text) * scale);
        drawScaledString(guiGraphics, text, centerX - width / 2, y, color, scale);
    }

    private static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width, y, color);
        guiGraphics.hLine(x, x + width, y + height, color);
        guiGraphics.vLine(x, y, y + height, color);
        guiGraphics.vLine(x + width, y, y + height, color);
    }

    private static int nodeFillColor(SkillNodeState state) {
        return switch (state) {
            case LEARNED -> 0xD61B3552;
            case AVAILABLE -> 0xD634244A;
            case LOCKED -> 0xD2131820;
        };
    }

    private static int innerCardColor(SkillNodeState state) {
        return switch (state) {
            case LEARNED -> 0xC8172432;
            case AVAILABLE -> 0xC8201733;
            case LOCKED -> 0xC612161D;
        };
    }

    private static int nodeBorderColor(SkillNodeState state) {
        return switch (state) {
            case LEARNED -> 0xFF6AB9FF;
            case AVAILABLE -> 0xFFB78BFF;
            case LOCKED -> 0xFF495767;
        };
    }

    private static int hoverBorderColor(SkillNodeState state) {
        return switch (state) {
            case LEARNED -> 0xFF96D4FF;
            case AVAILABLE -> 0xFFF1C56D;
            case LOCKED -> 0xFF7A8A99;
        };
    }

    private static int hoverGlowColor(SkillNodeState state) {
        return switch (state) {
            case LEARNED -> 0xAA75CAFF;
            case AVAILABLE -> 0xAAF4D06F;
            case LOCKED -> 0x664D5866;
        };
    }

    private static int titleColor(SkillNodeState state) {
        return switch (state) {
            case LEARNED -> 0xFFF2F8FF;
            case AVAILABLE -> 0xFFFFF3DA;
            case LOCKED -> 0xFFBAC3CF;
        };
    }

    private static int stateTextColor(SkillNodeState state) {
        return switch (state) {
            case LEARNED -> 0xFF7AD0FF;
            case AVAILABLE -> 0xFFF4D06F;
            case LOCKED -> 0xFF8E98A4;
        };
    }

    private static int iconBorderColor(SkillNodeState state) {
        return switch (state) {
            case LEARNED -> 0xFF6AB9FF;
            case AVAILABLE -> 0xFFF4D06F;
            case LOCKED -> 0xFF6A7480;
        };
    }

    private static int iconInnerColor(SkillNodeState state) {
        return switch (state) {
            case LEARNED -> 0xFF16344D;
            case AVAILABLE -> 0xFF31253E;
            case LOCKED -> 0xFF1A2028;
        };
    }

    private static int edgeColor(SkillTreeNode from, SkillTreeNode to) {
        if (from.state() == SkillNodeState.LEARNED && to.state() == SkillNodeState.LEARNED) {
            return 0xFF7CD4FF;
        }
        if (to.state() == SkillNodeState.AVAILABLE) {
            return 0xDDF0C66B;
        }
        return 0x88424D5B;
    }

    private String formatPrerequisites(SkillTreeNode node) {
        if (node.prerequisites().isEmpty()) {
            return AddonUiFont.translatable("screen.eltenaaddon.skill_tree.prerequisites.none").getString();
        }
        List<String> labels = new ArrayList<>();
        for (String prerequisite : node.prerequisites()) {
            SkillTreeNode found = nodeById.get(prerequisite);
            labels.add(found != null ? found.name() : TreeDisplayFormatter.formatRequirementValue(prerequisite));
        }
        return String.join(", ", labels);
    }

    private List<String> nodeDetailLines(SkillTreeNode node) {
        List<String> lines = new ArrayList<>();
        int requiredLevel = nodeRequiredLevels.getOrDefault(node.id(), 0);
        if (requiredLevel > 0) {
            lines.add(AddonUiFont.translatable("screen.eltenaaddon.skill_tree.node.required_level", requiredLevel).getString());
            lines.add(AddonUiFont.translatable("screen.eltenaaddon.skill_tree.node.cost", node.requiredPoints()).getString());
            return lines;
        }
        lines.add(AddonUiFont.translatable("screen.eltenaaddon.skill_tree.node.rank", node.currentRank(), node.maxRank()).getString());
        lines.add(AddonUiFont.translatable("screen.eltenaaddon.skill_tree.node.cost", node.requiredPoints()).getString());
        return lines;
    }

    private static String stateLabel(SkillNodeState state) {
        return switch (state) {
            case LEARNED -> AddonUiFont.translatable("screen.eltenaaddon.skill_tree.state.learned").getString();
            case AVAILABLE -> AddonUiFont.translatable("screen.eltenaaddon.skill_tree.state.available").getString();
            case LOCKED -> AddonUiFont.translatable("screen.eltenaaddon.skill_tree.state.locked").getString();
        };
    }

    private String skillTreeSyncStatusText() {
        if (payloadBacked) {
            return AddonUiFont.translatable(
                "screen.eltenaaddon.sync.live_nodes",
                AddonUiFont.translatable("screen.eltenaaddon.skill_tree").getString(),
                ModSyncClient.lastSkillTreeNodeCount()
            ).getString();
        }
        return AddonUiFont.translatable("screen.eltenaaddon.sync.mock").getString();
    }

    private String shortSyncError() {
        String error = ModSyncClient.lastSkillTreeSyncError();
        if (error == null || error.isBlank()) {
            return "";
        }
        return error.length() > 42 ? error.substring(0, 42) + "..." : error;
    }

    private void playNodeSound(SkillNodeState state) {
        switch (state) {
            case LEARNED -> TreeSoundHelper.playSelect();
            case AVAILABLE -> TreeSoundHelper.playSuccess();
            case LOCKED -> TreeSoundHelper.playFailure();
        }
    }

    private void openMenu() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new EltenaMenuScreen());
        }
    }

    private record NodeBounds(int x, int y, int width, int height) {
    }

    private record NodePlacement(int x, int y) {
    }

    private record GraphBounds(int minX, int minY, int maxX, int maxY) {
        private static GraphBounds empty() {
            return new GraphBounds(0, 0, 0, 0);
        }

        private int width() {
            return Math.max(0, maxX - minX);
        }

        private int height() {
            return Math.max(0, maxY - minY);
        }
    }

    private record LayoutMetrics(
        int rootLeft,
        int rootTop,
        int rootWidth,
        int rootHeight,
        int canvasLeft,
        int canvasTop,
        int canvasRight,
        int canvasBottom
    ) {
        private static LayoutMetrics defaultLayout() {
            return new LayoutMetrics(0, 12, AddonScreenLayout.ROOT_WIDTH, AddonScreenLayout.ROOT_HEIGHT, SAFE_MARGIN, 67, AddonScreenLayout.ROOT_WIDTH - SAFE_MARGIN, 394);
        }

        private int rootRight() {
            return rootLeft + rootWidth;
        }

        private int rootBottom() {
            return rootTop + rootHeight;
        }
    }
}
