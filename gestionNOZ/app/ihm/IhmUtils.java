package app.ihm;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;

/**
 * ══════════════════════════════════════════════════════════════
 *  IhmUtils — Palette light moderne & composants partagés
 *
 *  REFONTE VISUELLE :
 *  • Style light professionnel (fond blanc/gris très clair)
 *  • Moins dense : marges plus généreuses, police plus lisible
 *  • Boutons arrondis avec couleurs nettes
 *  • Tableaux aérés avec alternance de lignes
 *  • Typographie cohérente (Segoe UI / SansSerif 13-14)
 * ══════════════════════════════════════════════════════════════
 */
public final class IhmUtils
{
	// ── Palette principale ────────────────────────────────────────────────
	/** Fond général de l'application */
	public static final Color FOND      = new Color(250, 251, 253);
	/** Surface des cartes / panneaux blancs */
	public static final Color SURFACE   = Color.WHITE;
	/** Fond alterné des lignes de tableau (pair) */
	public static final Color LIGNE_ALT = new Color(246, 248, 251);
	/** Bordures légères */
	public static final Color BORD      = new Color(220, 224, 232);
	/** Bordure plus marquée (séparateurs) */
	public static final Color BORD_FORT = new Color(200, 206, 218);
	/** En-tête (barre du haut) */
	public static final Color HEADER    = new Color(26, 32, 44);
	/** Texte principal */
	public static final Color TEXTE     = new Color(26, 32, 44);
	/** Texte secondaire / légendes */
	public static final Color TEXTE_SEC = new Color(100, 112, 132);
	/** Fond des en-têtes de tableau */
	public static final Color GRILLE    = new Color(236, 239, 245);
	/** Sélection tableau */
	public static final Color SEL       = new Color(219, 234, 254);

	// ── Couleurs d'action ─────────────────────────────────────────────────
	/** Bleu principal — actions primaires */
	public static final Color BLEU      = new Color(37, 99, 235);
	/** Bleu hover */
	public static final Color BLEU_H    = new Color(29, 78, 216);
	/** Vert — validation, succès */
	public static final Color VERT      = new Color(22, 163, 74);
	/** Rouge — erreur, suppression */
	public static final Color ROUGE     = new Color(220, 38, 38);
	/** Ambre — avertissement */
	public static final Color AMBER     = new Color(217, 119, 6);
	/** Fond subtil bleu pour infos */
	public static final Color INFO      = new Color(239, 246, 255);
	/** Gris clair (bouton neutre) */
	public static final Color GRIS_BTN  = new Color(241, 245, 249);
	/** Gris clair — fond de champ */
	public static final Color GRIS_C    = new Color(246, 248, 250);

	private IhmUtils() {}

	// ══════════════════════════════════════════════════════════════════════
	//  BOUTONS
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Bouton principal coloré avec coins arrondis et hover.
	 * Usage : actions principales (Affecter, Valider, Sauvegarder…)
	 */
	public static JButton bouton(String texte, Color bg, Color fg)
	{
		JButton b = new JButton(texte)
		{
			@Override protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color c = getModel().isRollover() ? bg.darker() : bg;
				g2.setColor(c);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
				g2.dispose();
				super.paintComponent(g);
			}
		};
		b.setForeground(fg);
		b.setFont(new Font("SansSerif", Font.PLAIN, 13));
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setContentAreaFilled(false);
		b.setOpaque(false);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setBorder(new EmptyBorder(7, 16, 7, 16));
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		return b;
	}

	/**
	 * Bouton discret (outline) pour actions secondaires.
	 */
	public static JButton boutonSecondaire(String texte)
	{
		JButton b = new JButton(texte)
		{
			@Override protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color bg = getModel().isRollover() ? GRIS_BTN.darker() : GRIS_BTN;
				g2.setColor(bg);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
				g2.setColor(BORD_FORT);
				g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 8, 8));
				g2.dispose();
				super.paintComponent(g);
			}
		};
		b.setForeground(TEXTE);
		b.setFont(new Font("SansSerif", Font.PLAIN, 13));
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setContentAreaFilled(false);
		b.setOpaque(false);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setBorder(new EmptyBorder(7, 14, 7, 14));
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		return b;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  LABELS
	// ══════════════════════════════════════════════════════════════════════

	/** Label de section (titre de groupe) */
	public static JLabel labelSection(String texte)
	{
		JLabel l = new JLabel(texte);
		l.setFont(new Font("SansSerif", Font.BOLD, 13));
		l.setForeground(TEXTE);
		l.setBorder(new EmptyBorder(0, 0, 6, 0));
		return l;
	}

	/** Petit label gris pour champ de formulaire */
	public static void ajLabel(JPanel p, String texte)
	{
		JLabel l = new JLabel(texte);
		l.setFont(new Font("SansSerif", Font.PLAIN, 11));
		l.setForeground(TEXTE_SEC);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(Box.createVerticalStrut(6));
		p.add(l);
	}

	/** Badge coloré (pastille avec texte court) */
	public static JLabel badge(String texte, Color bg, Color fg)
	{
		JLabel l = new JLabel(texte)
		{
			@Override protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(bg);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
				g2.dispose();
				super.paintComponent(g);
			}
		};
		l.setFont(new Font("SansSerif", Font.BOLD, 10));
		l.setForeground(fg);
		l.setOpaque(false);
		l.setBorder(new EmptyBorder(2, 7, 2, 7));
		l.setHorizontalAlignment(SwingConstants.CENTER);
		return l;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  TABLEAUX
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Tableau avec style moderne : lignes aérées, alternance, header propre.
	 */
	public static JTable creerTable(DefaultTableModel model)
	{
		JTable t = new JTable(model)
		{
			@Override public Component prepareRenderer(
					javax.swing.table.TableCellRenderer r, int row, int col)
			{
				Component c = super.prepareRenderer(r, row, col);
				if (!isRowSelected(row))
					c.setBackground(row % 2 == 0 ? SURFACE : LIGNE_ALT);
				return c;
			}
		};
		t.setRowHeight(30);
		t.setFont(new Font("SansSerif", Font.PLAIN, 13));
		t.setShowVerticalLines(false);
		t.setShowHorizontalLines(true);
		t.setGridColor(BORD);
		t.setIntercellSpacing(new Dimension(10, 0));
		t.setSelectionBackground(SEL);
		t.setSelectionForeground(TEXTE);
		t.setBackground(SURFACE);
		t.setFillsViewportHeight(true);

		t.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
		t.getTableHeader().setBackground(GRILLE);
		t.getTableHeader().setForeground(TEXTE_SEC);
		t.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORD_FORT));
		t.getTableHeader().setReorderingAllowed(false);
		t.getTableHeader().setPreferredSize(new Dimension(0, 32));

		return t;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  PANNEAUX / CARTES
	// ══════════════════════════════════════════════════════════════════════

	/** Carte blanche avec ombre légère et bord arrondi (simulé par bordure). */
	public static JPanel carte(int padding)
	{
		JPanel p = new JPanel();
		p.setBackground(SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORD, 1),
			new EmptyBorder(padding, padding, padding, padding)));
		return p;
	}

	/** Panneau de formulaire avec fond blanc et padding. */
	public static JPanel panelFormulaire(int largeur)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		if (largeur > 0) p.setPreferredSize(new Dimension(largeur, 0));
		p.setBackground(SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORD, 1),
			new EmptyBorder(16, 16, 16, 16)));
		return p;
	}

	/** Panneau blanc simple avec padding. */
	public static JPanel panelBlanc(int padding)
	{
		JPanel p = new JPanel();
		p.setBackground(SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORD, 1),
			new EmptyBorder(padding, padding, padding, padding)));
		return p;
	}

	/** Séparateur horizontal fin. */
	public static JSeparator separateur()
	{
		JSeparator s = new JSeparator();
		s.setForeground(BORD);
		s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		return s;
	}

	/**
	 * Enveloppe un tableau dans un JScrollPane avec style cohérent.
	 */
	public static JScrollPane scrollTable(JTable table)
	{
		JScrollPane scroll = new JScrollPane(table);
		scroll.setBorder(BorderFactory.createLineBorder(BORD, 1));
		scroll.getViewport().setBackground(SURFACE);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CHAMPS DE SAISIE
	// ══════════════════════════════════════════════════════════════════════

	/** Crée un JTextField stylisé avec bordure arrondie simulée. */
	public static JTextField champ(String placeholder)
	{
		JTextField f = new JTextField();
		f.setFont(new Font("SansSerif", Font.PLAIN, 13));
		f.setForeground(TEXTE);
		f.setBackground(SURFACE);
		f.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORD_FORT, 1),
			new EmptyBorder(6, 10, 6, 10)));
		f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		return f;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  UTILITAIRES
	// ══════════════════════════════════════════════════════════════════════

	/** Formate les heures avec 1 décimale. */
	public static String fmt(double h)
	{
		return String.format("%.1f h", h);
	}

	/** Formate un montant en euros. */
	public static String euro(int v)
	{
		return String.format("%,d €", v).replace(',', ' ');
	}

	/**
	 * Installe le Look & Feel système sur Windows (Nimbus sinon)
	 * et applique les sur-couches de couleur globales.
	 * Appeler UNE FOIS au démarrage de main().
	 */
	public static void installerLookAndFeel()
	{
		try {
			// Essayer FlatLaf ou Nimbus selon ce qui est dispo
			for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels())
			{
				if ("Nimbus".equals(info.getName()))
				{
					UIManager.setLookAndFeel(info.getClassName());
					break;
				}
			}
		} catch (Exception ignored) {
			try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
			catch (Exception ignored2) {}
		}

		// Sur-couches globales
		UIManager.put("Panel.background",            FOND);
		UIManager.put("TabbedPane.background",       FOND);
		UIManager.put("TabbedPane.contentAreaColor", FOND);
		UIManager.put("TabbedPane.selected",         SURFACE);
		UIManager.put("TabbedPane.font",             new Font("SansSerif", Font.PLAIN, 13));
		UIManager.put("Table.background",            SURFACE);
		UIManager.put("Table.alternateRowColor",     LIGNE_ALT);
		UIManager.put("Table.selectionBackground",   SEL);
		UIManager.put("Table.selectionForeground",   TEXTE);
		UIManager.put("Table.gridColor",             BORD);
		UIManager.put("ScrollPane.background",       FOND);
		UIManager.put("TextField.background",        SURFACE);
		UIManager.put("ComboBox.background",         SURFACE);
		UIManager.put("Button.font",                 new Font("SansSerif", Font.PLAIN, 13));
		UIManager.put("Label.font",                  new Font("SansSerif", Font.PLAIN, 13));
		UIManager.put("OptionPane.background",       SURFACE);
		UIManager.put("OptionPane.messageForeground",TEXTE);
	}
}