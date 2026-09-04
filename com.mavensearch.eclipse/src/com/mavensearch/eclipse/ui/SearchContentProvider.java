package com.mavensearch.eclipse.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.PlatformUI;

import com.mavensearch.eclipse.model.Artifact;
import com.mavensearch.eclipse.service.VersionService.VersionPage;

/** 结果树内容：artifact 懒展开版本；「显示全部」/「重试」均在此处理。 */
final class SearchContentProvider implements ITreeContentProvider {

    private static final Object[] EMPTY = new Object[0];

    private final java.util.function.Supplier<TreeViewer> viewer;
    /** ga → 当前子节点（含 Loading/Error/More），保证展开结果稳定。 */
    private final Map<String, Object[]> children = new ConcurrentHashMap<>();
    private final Map<String, VersionPage> fullPages = new HashMap<>();
    private final Map<String, Boolean> loading = new ConcurrentHashMap<>();

    SearchContentProvider(java.util.function.Supplier<TreeViewer> viewer) {
        this.viewer = viewer;
    }

    @Override
    public Object[] getElements(Object inputElement) {
        if (inputElement instanceof List<?> list) {
            return SearchNodes.artifactNodes(
                    list.stream().map(o -> (Artifact) o).toList()).toArray();
        }
        return EMPTY;
    }

    @Override
    public boolean hasChildren(Object element) {
        return element instanceof SearchNodes.ArtifactNode;
    }

    @Override
    public Object[] getChildren(Object parentElement) {
        if (!(parentElement instanceof SearchNodes.ArtifactNode a)) {
            return EMPTY;
        }
        String key = a.artifact().name();
        Object[] existing = children.get(key);
        if (existing == null) {
            children.put(key, new Object[] { SearchNodes.LoadingNode.INSTANCE });
            loadAsync(a.artifact(), key, false);
            return new Object[] { SearchNodes.LoadingNode.INSTANCE };
        }
        return existing;
    }

    /** 展开 MoreNode / 重试 ErrorNode 时由 View 调用。 */
    void expandSpecial(Object node) {
        if (node instanceof SearchNodes.MoreNode m) {
            loadAsync(m.artifact(), m.artifact().name(), true);
        } else if (node instanceof SearchNodes.ErrorNode e) {
            children.remove(e.artifact().name());
            loadAsync(e.artifact(), e.artifact().name(), false);
        }
    }

    private void loadAsync(Artifact artifact, String key, boolean all) {
        if (Boolean.TRUE.equals(loading.putIfAbsent(key, Boolean.TRUE))) {
            return;
        }
        if (all) {
            children.put(key, new Object[] { SearchNodes.LoadingNode.INSTANCE });
            refreshNode(artifact);
        }
        Job.create("Load versions", m -> {
            try {
                VersionPage page = Activator.getDefault().getVersionService()
                        .load(artifact.groupId(), artifact.artifactId(), all);
                PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
                    loading.remove(key);
                    List<Object> nodes = new ArrayList<>();
                    for (var v : page.versions()) {
                        nodes.add(new SearchNodes.VersionNode(artifact, v));
                    }
                    if (page.totalVersions() > page.versions().size()) {
                        nodes.add(new SearchNodes.MoreNode(artifact));
                    }
                    children.put(key, nodes.toArray());
                    refreshNode(artifact);
                });
            } catch (Exception ex) {
                PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
                    loading.remove(key);
                    children.put(key, new Object[] {
                            new SearchNodes.ErrorNode(artifact, "Failed to load versions — click to retry") });
                    refreshNode(artifact);
                });
            }
            return org.eclipse.core.runtime.Status.OK_STATUS;
        }).schedule();
    }

    private void refreshNode(Artifact artifact) {
        TreeViewer v = viewer.get();
        if (v != null && !v.getControl().isDisposed()) {
            v.refresh(artifact2Node(v, artifact), true);
        }
    }

    private static Object artifact2Node(TreeViewer v, Artifact artifact) {
        for (Object el : (Object[]) v.getInput()) {
            if (el instanceof SearchNodes.ArtifactNode an && an.artifact().name().equals(artifact.name())) {
                return an;
            }
        }
        return artifact;
    }

    @Override
    public Object getParent(Object element) {
        if (element instanceof SearchNodes.VersionNode v) {
            return new SearchNodes.ArtifactNode(v.artifact());
        }
        return null;
    }

    @Override
    public void dispose() {
        // 无资源
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        children.clear();
        loading.clear();
    }
}
