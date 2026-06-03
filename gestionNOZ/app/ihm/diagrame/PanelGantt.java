package app.ihm.diagrame;

import app.IControleur;
import app.ihm.IhmUtils;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.swing.*;

/**
 * Panneau de rendu du diagramme de Gantt pour les lots.
 *
 * CORRECTIF — Double couche d'avancement :
 * ─────────────────────────────────────────
 * Chaque barre se compose désormais de deux couches superposées :
 *
 *   1. Couche THÉORIQUE (fond, opaque) :
 *      • de dateDebut → dateFinThéorique
 *      • couleur ACE (ou VERT si terminé)
 *      • identique à l'original
 *
 *   2. Couche RÉELLE (par-dessus, semi-transparente) :
 *      • de dateDebut → NOW si en cours, ou dateDebut → dateFin si terminé
 *      • largeur proportionnelle à l'avancement réel (nbPieceEtiq / nbPieces)
 *      • couleur verte plus foncée pour les lots terminés,
 *        orange pour les lots en cours, bleue translucide sinon
 *      • dessinée à l'intérieur de la barre théorique
 *
 * La couche réelle permet de voir d'un coup d'œil si le lot avance
 * conformément au planning théorique.
 */
public class PanelGantt extends JPanel
{
	private ArrayList<Lot> lots = new ArrayList<>();
	private IControleur     ctrl;

	private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

	// ================= CONFIG =================
	private static final int LEFT      = 120;
	private static final int TOP       = 80;
	private static final int ROW       = 45;
	private static final int SCALE     = 1;          // 1 min = 1 px
	private static final int DAY_MINUTES = (9 * 60); // 8h15 → 16h15
	private static final int DAY_WIDTH = DAY_MINUTES * SCALE;

	private final String[] DAYS = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi"};

	// Hauteur de la barre théorique et de la couche réelle
	private static final int BAR_H      = 28;  // hauteur totale
	private static final int REAL_H     = 10;  // hauteur couche réelle (en bas de la barre)

	public PanelGantt(IControleur ctrl)
	{
		setBackground(Color.WHITE);
		this.ctrl = ctrl;
	}

	// ================= DATA =================

	public void setLots(List<Lot> l)
	{
		lots = new ArrayList<>();
		if (l != null)
		{
			for (Lot x : l)
			{
				if (x == null) continue;
				if (x.getDateDebut() == null || x.getDateDebut().isEmpty()) continue;
				lots.add(x);
			}
		}
		lots.sort(Comparator.comparing(this::safeStart));
		revalidate();
		repaint();
	}

	// ================= PAINT =================

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		drawGrid(g2);
		drawLots(g2);
		drawNowLine(g2); // ligne verticale "maintenant"
	}

	// ================= GRID =================

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

	// ================= LIGNE "MAINTENANT" =================

	/**
	 * Trace une ligne rouge verticale à la position de l'heure actuelle.
	 * Aide à voir visuellement les lots en retard ou en avance.
	 */
	private void drawNowLine(Graphics2D g2)
	{
		LocalDateTime now = LocalDateTime.now();
		int dayOfWeek = now.getDayOfWeek().getValue() - 1; // 0=lundi
		if (dayOfWeek < 0 || dayOfWeek > 4) return;       // week-end : pas de ligne

		int nowX = LEFT + toWeekMinutes(now) + 15;
		if (nowX < LEFT) return;

		g2.setColor(new Color(220, 50, 50, 180));
		Stroke ancien = g2.getStroke();
		g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
			10f, new float[]{6f, 4f}, 0f));
		g2.drawLine(nowX, TOP, nowX, TOP + lots.size() * ROW + 20);
		g2.setStroke(ancien);

		// Label "Maintenant"
		g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
		g2.setColor(new Color(200, 30, 30));
		g2.drawString("↓ Now", nowX - 14, TOP - 8);
	}

	// ================= LOTS =================

	private void drawLots(Graphics2D g2)
	{
		g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));

		for (int i = 0; i < lots.size(); i++)
		{
			Lot    l        = lots.get(i);
			Ace    a        = ctrl.getAceDuLot(l);
			String nomAce   = (a != null) ? a.getNom() : "—";
			Color  colorAce = (a != null) ? a.getColor() : Color.LIGHT_GRAY;

			LocalDateTime start = safeStart(l);
			LocalDateTime endT  = safeEndTheorique(l); // fin théorique

			int startX = LEFT + toWeekMinutes(start) + 15;
			int endX   = LEFT + toWeekMinutes(endT)  + 15;

			if (endX < startX)
				endX = LEFT + DAYS.length * DAY_WIDTH + 50;

			int widthT = Math.max(10, endX - startX);
			int y      = TOP + i * ROW + 8;

			// ── 1. BARRE THÉORIQUE (fond) ──────────────────────────────
			boolean estTermine = l.getdateFin() != null && !l.getdateFin().isEmpty();
			Color couleurBarre = estTermine ? IhmUtils.VERT : colorAce;

			g2.setColor(couleurBarre);
			g2.fillRoundRect(startX, y, widthT, BAR_H, 10, 10);
			g2.setColor(couleurBarre.darker());
			g2.drawRoundRect(startX, y, widthT, BAR_H, 10, 10);

			// ── 2. COUCHE RÉELLE (par-dessus, en bas de la barre) ──────
			drawCoucheReelle(g2, l, startX, y, widthT, estTermine);

			// ── 3. TEXTE ────────────────────────────────────────────────
			g2.setColor(Color.BLACK);
			String txt = "[" + nomAce + "] CDE " + l.getNumCDE()
				+ "  " + String.format("%02d:%02d", start.getHour(), start.getMinute())
				+ " → " + String.format("%02d:%02d", endT.getHour(),  endT.getMinute());
			g2.drawString(txt, startX + 5, y + 14);

			// ── 4. POURCENTAGE D'AVANCEMENT (texte) ────────────────────
			int pct = calculPctAvancement(l);
			if (pct > 0)
			{
				g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
				g2.setColor(Color.WHITE);
				g2.drawString(pct + "%", startX + widthT - 34, y + 14);
				g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
			}

			// ── 5. LABEL GAUCHE (nom du lot) ───────────────────────────
			g2.setColor(Color.BLACK);
			String labelGauche = "CDE " + l.getNumCDE();
			g2.drawString(labelGauche, 5, y + BAR_H / 2 + 4);
		}
	}

	/**
	 * Dessine la couche d'avancement réel à l'intérieur de la barre théorique.
	 *
	 * Logique :
	 *   • Si le lot est terminé   → couche verte pleine (100%)
	 *   • Si le lot est en cours  → couche proportionnelle à nbPieceEtiq/nbPieces
	 *                               la largeur représente l'avancement réel étiquetage
	 *   La couche est dessinée sur les 10px du bas de la barre pour rester lisible
	 *   sans masquer le texte de la barre théorique.
	 */
	private void drawCoucheReelle(Graphics2D g2, Lot l,
	                              int startX, int y, int widthT, boolean estTermine)
	{
		int pct = calculPctAvancement(l);
		if (pct <= 0) return;

		int widthReel = (int)(widthT * pct / 100.0);
		if (widthReel <= 0) return;

		// Position : bande en bas de la barre théorique
		int yReel = y + BAR_H - REAL_H;

		// Couleur : vert foncé si terminé, orange si en cours mais pas à 100%
		Color couleurReel;
		if (estTermine || pct >= 100)
			couleurReel = new Color(22, 163, 74, 220);   // vert soutenu
		else if (pct >= 70)
			couleurReel = new Color(34, 197, 94, 200);   // vert clair
		else if (pct >= 40)
			couleurReel = new Color(234, 179, 8, 200);   // orange/jaune
		else
			couleurReel = new Color(249, 115, 22, 200);  // orange vif

		// Dessin avec clip pour rester dans les coins arrondis de la barre théorique
		Shape clipAvant = g2.getClip();
		g2.setClip(startX, y, widthT, BAR_H);

		g2.setColor(couleurReel);
		// Coins arrondis seulement à droite si pas encore à 100%
		if (pct >= 99)
			g2.fillRoundRect(startX, yReel, widthReel, REAL_H, 8, 8);
		else
			g2.fillRect(startX, yReel, widthReel, REAL_H);

		// Bordure légère
		g2.setColor(couleurReel.darker());
		if (pct >= 99)
			g2.drawRoundRect(startX, yReel, widthReel, REAL_H, 8, 8);
		else
			g2.drawRect(startX, yReel, widthReel, REAL_H);

		g2.setClip(clipAvant);
	}

	/**
	 * Calcule le pourcentage d'avancement réel du lot.
	 * Basé sur l'avancement étiquetage (nbPieceEtiq / nbPieces).
	 * Si le lot est terminé (dateFin renseignée) → 100%.
	 */
	private int calculPctAvancement(Lot l)
	{
		// Lot terminé = 100%
		if (l.getdateFin() != null && !l.getdateFin().isEmpty()) return 100;

		// Lot pas encore commencé
		if (l.getDateDebut() == null || l.getDateDebut().isEmpty()) return 0;

		// Avancement via suivi production
		if (l.getSuivieProd() != null && l.getNbPieces() > 0)
		{
			int etiq = l.getSuivieProd().getNbPieceEtiq();
			if (etiq > 0)
				return Math.min(100, (int)(etiq * 100.0 / l.getNbPieces()));
		}

		// Fallback : avancement temporel (temps écoulé / temps théorique)
		try
		{
			LocalDateTime debut = LocalDateTime.parse(l.getDateDebut(), fmt);
			LocalDateTime finT  = LocalDateTime.parse(l.getdateFinT(), fmt);
			LocalDateTime now   = LocalDateTime.now();
			if (now.isBefore(debut)) return 0;
			long totalMin   = java.time.Duration.between(debut, finT).toMinutes();
			long ecoulesMin = java.time.Duration.between(debut, now).toMinutes();
			if (totalMin <= 0) return 0;
			return Math.min(100, (int)(ecoulesMin * 100.0 / totalMin));
		}
		catch (Exception e) { return 0; }
	}

	// ================= TIME SYSTEM =================

	private int toWeekMinutes(LocalDateTime t)
	{
		int day = t.getDayOfWeek().getValue() - 1;
		if (day < 0) day = 0;
		if (day > 4) day = 4;
		int minutesDay      = t.getHour() * 60 + t.getMinute();
		int startDay        = 8 * 60 + 15;
		int minutesSinceStart = minutesDay - startDay;
		return day * DAY_WIDTH + minutesSinceStart;
	}

	@Override
	public Dimension getPreferredSize()
	{
		int width  = LEFT + DAYS.length * DAY_WIDTH + 100;
		int height = TOP  + Math.max(1, lots.size()) * ROW + 100;
		return new Dimension(width, height);
	}

	// ================= SAFE PARSE =================

	private LocalDateTime safeStart(Lot l)
	{
		try { return LocalDateTime.parse(l.getDateDebut(), fmt); }
		catch (Exception e) { return LocalDateTime.now(); }
	}

	/** Fin théorique uniquement (pour la barre de fond). */
	private LocalDateTime safeEndTheorique(Lot l)
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
