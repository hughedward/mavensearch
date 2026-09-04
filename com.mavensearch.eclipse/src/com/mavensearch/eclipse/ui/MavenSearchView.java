package com.mavensearch.eclipse.ui;

import java.util.List;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import com.mavensearch.eclipse.model.Artifact;

/** 主视图：搜索框 + 结果树（依赖名称 / 使用量两列）。 */
public final class MavenSearchView extends ViewPart {

    public static final String ID = "com.mavensearch.eclipse.view";

    private Text searchInput;
    private TreeViewer viewer;
    private Label notice;

    @Override
    public void createPartControl(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        GridLayout grid = new GridLayout(1, false);
        grid.marginWidth = 0;
        grid.marginHeight = 0;
        root.setLayout(grid);

        searchInput = new Text(root, SWT.SEARCH | SWT.BORDER | SWT.CANCEL);
        searchInput.setMessage("Search artifacts (e.g. spring boot, or groupId:artifactId)");
        searchInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        searchInput.addModifyListener(e -> {
            // 防抖 200ms：每次击键调度一次定时器；文本自调度起又变了的定时器自然作废，
            // 收敛为输入 settle 后恰好一次搜索
            final String q = searchInput.getText();
            org.eclipse.swt.widgets.Display.getCurrent().timerExec(200, () -> {
                if (!searchInput.isDisposed() && q.equals(searchInput.getText())) {
                    runSearch(q);
                }
            });
        });

        notice = new Label(root, SWT.NONE);
        notice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        notice.setVisible(false);

        viewer = new TreeViewer(root, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        viewer.getTree().setHeaderVisible(true);
        viewer.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        TreeViewerColumn nameCol = new TreeViewerColumn(viewer, SWT.LEFT);
        nameCol.getColumn().setText("Artifact");
        nameCol.getColumn().setWidth(420);
        nameCol.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return SearchNodes.artifactText(element);
            }
        });

        TreeViewerColumn usageCol = new TreeViewerColumn(viewer, SWT.RIGHT);
        usageCol.getColumn().setText("Usage");
        usageCol.getColumn().setWidth(110);
        usageCol.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return SearchNodes.usageText(element);
            }
        });

        viewer.setContentProvider(new SearchContentProvider(this::viewer));
        viewer.setInput(List.<Artifact>of());

        searchInput.setFocus();
    }

    private TreeViewer viewer() {
        return viewer;
    }

    void runSearch(String query) {
        SearchServiceAsync.search(Activator.getDefault().getSearchService()::search, query, 20, result -> {
            if (searchInput.isDisposed() || !query.equals(searchInput.getText())) {
                return; // 勘误 #23：过期结果（乱序回投）或视图已关闭——丢弃
            }
            // 提示行（spec §7）：在线兜底提示 / 无结果建议输完整 g:a
            String hint;
            if (!result.fromIndex() && !result.artifacts().isEmpty()) {
                hint = "Index downloading — online fallback, results may be incomplete";
            } else if (result.artifacts().isEmpty()) {
                hint = "No results — try other keywords, or type a full groupId:artifactId";
            } else {
                hint = "";
            }
            notice.setText(hint);
            notice.setVisible(!hint.isEmpty());
            viewer.setInput(result.artifacts());
            viewer.refresh();
        });
    }

    @Override
    public void setFocus() {
        searchInput.setFocus();
    }
}
