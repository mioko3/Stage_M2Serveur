package app.ihm.diagrame;

import app.IControleur;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class PanelGantt extends JPanel
{
	private ArrayList<Lot> lots = new ArrayList<>();
	private IControleur    ctrl;

	private final DateTimeFormatter fmt =
		DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

	// ── Config ────────────────────────────────────────────────────────
	private static final int LEFT        = 120;
	private static final int TOP         = 80;
	private static final int ROW         = 45;
	private static final int SCALE       = 1;           // 1 min = 1 px
	private static final int DAY_MINUTES = 9 * 60;      // 9h de travail
	private static final int DAY_WIDTH   = DAY_MINUTES * SCALE;

	private final String[] DAYS = {"Lun", "Mar", "Mer", "Jeu", "Ven"};

	public PanelGantt(IControleur ctrl)
	{
		setBackground(Color.WHITE);
		this.ctrl = ctrl;
	}

	// ── Data ──────────────────────────────────────────────────────────

	public void setLots(List<Lot> l)
	{
		lots.clear();
		if (l != null)
			for (Lot x : l)
			{
				if (x == null) continue;
				if (x.getDateDebut() == null || x.getDateDebut().isEmpty()) continue;
				lots.add(x);
			}
		lots.sort(Comparator.comparing(this::safeStart));
		revalidate();
		repaint();
	}

	// ── Paint ─────────────────────────────────────────────────────────

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		drawGrid(g2);
		drawLots(g2);
	}

	// ── Grille ────────────────────────────────────────────────────────

	private void drawGrid(Graphics2D g2)
	{
		g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));

		for (int d = 0; d < DAYS.length; d++)
		{
			int baseX = LEFT + d * DAY_WIDTH;

			g2.setColor(new Color(245, 245, 245));
			g2.fillRect(baseX, TOP, DAY_WIDTH, getHeight());

			g2.setColor(Color.BLACK);
			g2.drawString(DAYS[d], baseX + 10, 40);

			for (int h = 8; h <= 16; h++)
			{
				int x = baseX + (h - 8) * 60;
				g2.setColor(new Color(220, 220, 220));
				g2.drawLine(x, TOP, x, getHeight());
				g2.setColor(Color.GRAY);
				g2.drawString(h + "h", x + 2, TOP - 5);
			}
		}
	}

	// ── Lots ──────────────────────────────────────────────────────────

	private void drawLots(Graphics2D g2)
	{
		for (int i = 0; i < lots.size(); i++)
		{
			Lot l = lots.get(i);
			Ace a = ctrl.getAceDuLot(l);
			String nomAce  = (a != null) ? a.getNom()   : "—";
			Color colorAce = (a != null) ? a.getColor() : Color.LIGHT_GRAY;

			LocalDateTime start = safeStart(l);
			LocalDateTime end   = safeEnd(l);

			int startX = LEFT + toWeekMinutes(start);
			int endX   = LEFT + toWeekMinutes(end);
			int width  = Math.max(10, endX - startX);
			int y      = TOP + i * ROW + 8;

			// Vert si terminé, couleur ACE sinon
			if (l.getdateFin() != null && !l.getdateFin().isEmpty())
				g2.setColor(new Color(34, 139, 34));
			else
				g2.setColor(colorAce);

			g2.fillRoundRect(startX, y, width, 28, 10, 10);
			g2.setColor(Color.BLACK);
			g2.drawRoundRect(startX, y, width, 28, 10, 10);

			String txt = "[" + nomAce + "] CDE " + l.getNumCDE()
				+ " " + String.format("%02d:%02d", start.getHour(), start.getMinute())
				+ " → " + String.format("%02d:%02d", end.getHour(), end.getMinute());

			g2.drawString(txt, startX + 5, y + 18);
		}
	}

	// ── Système de temps ──────────────────────────────────────────────

	/**
	 * Convertit une LocalDateTime en minutes depuis le début de la semaine
	 * (lundi 8h15 = 0).
	 */
	private int toWeekMinutes(LocalDateTime t)
	{
		int day             = t.getDayOfWeek().getValue() - 1; // lundi = 0
		int minutesDay      = t.getHour() * 60 + t.getMinute();
		int startDay        = 8 * 60 + 15; // 8h15
		int minutesSinceStart = minutesDay - startDay;
		return day * DAY_WIDTH + minutesSinceStart;
	}

	@Override
	public Dimension getPreferredSize()
	{
		int width  = LEFT + DAYS.length * DAY_WIDTH + 100;
		int height = TOP + Math.max(1, lots.size()) * ROW + 100;
		return new Dimension(width, height);
	}

	// ── Parse sécurisé ────────────────────────────────────────────────

	private LocalDateTime safeStart(Lot l)
	{
		try { return LocalDateTime.parse(l.getDateDebut(), fmt); }
		catch (Exception e) { return LocalDateTime.now(); }
	}

	private LocalDateTime safeEnd(Lot l)
	{
		try
		{
			if (l.getdateFin() != null && !l.getdateFin().isEmpty())
				return LocalDateTime.parse(l.getdateFin(), fmt);
			if (l.getdateFinT() != null && !l.getdateFinT().isEmpty())
				return LocalDateTime.parse(l.getdateFinT(), fmt);
		}
		catch (Exception ignored) {}
		return safeStart(l).plusHours(1);
	}
}
