package edu.calpoly.csc364.coordinator;

import edu.calpoly.csc364.common.Operation;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CoordinatorDashboard {
    private final JFrame frame = new JFrame("CSC 364 Distributed Producer-Consumer Coordinator");
    private final JTextArea events = new JTextArea();
    private final JLabel generated = new JLabel("Generated: 0");
    private final JLabel pending = new JLabel("Pending: 0");
    private final JLabel localDone = new JLabel("Local completed: 0");
    private final JLabel outsourced = new JLabel("Outsourced: 0");
    private final JLabel remoteDone = new JLabel("Remote completed: 0");
    private final JLabel recovered = new JLabel("Recovered: 0");
    private final JPanel workersPanel = new JPanel(new GridLayout(0, 2, 10, 10));
    private final Map<String, WorkerCard> workerCards = new ConcurrentHashMap<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public CoordinatorDashboard(int producers, int localWorkers) {
        SwingUtilities.invokeLater(() -> buildUi(producers, localWorkers));
    }

    private void buildUi(int producers, int localWorkers) {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1100, 700); frame.setLocationByPlatform(true);
        JPanel root = new JPanel(new BorderLayout(12, 12)); root.setBorder(new EmptyBorder(12, 12, 12, 12));
        JPanel left = new JPanel(new BorderLayout(8, 8)); left.setPreferredSize(new Dimension(520, 650));
        JPanel stats = new JPanel(new GridLayout(0, 2, 8, 4));
        stats.setBorder(BorderFactory.createTitledBorder("Coordinator"));
        stats.add(new JLabel("Running producers: " + producers)); stats.add(new JLabel("Local workers: " + localWorkers));
        stats.add(generated); stats.add(pending); stats.add(localDone); stats.add(outsourced); stats.add(remoteDone); stats.add(recovered);
        events.setEditable(false); events.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        left.add(stats, BorderLayout.NORTH); left.add(new JScrollPane(events), BorderLayout.CENTER);
        workersPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JScrollPane workersScroll = new JScrollPane(workersPanel); workersScroll.setBorder(BorderFactory.createTitledBorder("Remote Workers"));
        root.add(left, BorderLayout.WEST); root.add(workersScroll, BorderLayout.CENTER);
        frame.setContentPane(root); frame.setVisible(true);
    }

    public void log(String text) { onEdt(() -> { events.append(LocalTime.now().format(TIME) + "  " + text + "\n"); events.setCaretPosition(events.getDocument().getLength()); }); }
    public void operationGenerated(int p, Operation op, int q, CoordinatorStats s) { log("Producer " + p + " generated " + op.expression()); refresh(q, s); }
    public void localStarted(int w, Operation op, int q) { log("Local " + w + " solving " + op.expression()); setPending(q); }
    public void localCompleted(int w, Operation op, String value, int q, CoordinatorStats s) { log("Local " + w + " completed " + op.expression() + " = " + value); refresh(q, s); }
    public void outsourced(String worker, Operation op, int q, CoordinatorStats s) { card(worker).set("Busy", op.expression(), null, false); log("Outsourced " + op.expression() + " to " + worker); refresh(q, s); }
    public void remoteCompleted(String worker, Operation op, String value, int q, CoordinatorStats s) { card(worker).set("Available", "None", op.expression() + " = " + value, true); log("Remote result from " + worker + ": " + op.expression() + " = " + value); refresh(q, s); }
    public void recovered(String worker, Operation op, String reason, int q, CoordinatorStats s) { card(worker).set("Timed out", "None", null, false); log("Recovered " + op.expression() + " from " + worker + " (" + reason + ")"); refresh(q, s); }
    public void workerAvailable(String worker) { card(worker).setStatus("Available"); log("Worker available: " + worker); }
    public void workerIdle(String worker, String reason) { card(worker).set("Waiting", "None", null, false); log(worker + " waiting: " + reason); }
    public void workerStatus(String worker, String status) { card(worker).setStatus(status); log("Worker " + worker + " " + status); }

    private WorkerCard card(String id) {
        WorkerCard existing = workerCards.get(id); if (existing != null) return existing;
        WorkerCard created = new WorkerCard(id); WorkerCard prior = workerCards.putIfAbsent(id, created);
        WorkerCard chosen = prior == null ? created : prior;
        if (prior == null) onEdt(() -> { workersPanel.add(created.panel); workersPanel.revalidate(); workersPanel.repaint(); });
        return chosen;
    }
    private void refresh(int q, CoordinatorStats s) { onEdt(() -> { generated.setText("Generated: " + s.generated.get()); pending.setText("Pending: " + q); localDone.setText("Local completed: " + s.localCompleted.get()); outsourced.setText("Outsourced: " + s.outsourced.get()); remoteDone.setText("Remote completed: " + s.remoteCompleted.get()); recovered.setText("Recovered: " + s.recovered.get()); }); }
    private void setPending(int q) { onEdt(() -> pending.setText("Pending: " + q)); }
    private static void onEdt(Runnable r) { if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r); }

    private static final class WorkerCard {
        final JPanel panel = new JPanel(new GridLayout(0, 1, 3, 3));
        final JLabel status = new JLabel("Status: connected"); final JLabel current = new JLabel("Current: None");
        final JLabel count = new JLabel("Completed: 0"); final JLabel last = new JLabel("Last: None"); int completed;
        WorkerCard(String id) { panel.setBorder(BorderFactory.createTitledBorder(id)); panel.add(status); panel.add(current); panel.add(count); panel.add(last); }
        void setStatus(String value) { onEdt(() -> status.setText("Status: " + value)); }
        void set(String st, String cur, String lastValue, boolean increment) { onEdt(() -> { status.setText("Status: " + st); current.setText("Current: " + cur); if (increment) count.setText("Completed: " + (++completed)); if (lastValue != null) last.setText("Last: " + lastValue); }); }
    }
}
