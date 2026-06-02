package app.ihm;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

/**
 * ═══════════════════════════════════════════════════════════════
 *  IhmUtils — Design System unifié v2
 *
 *  Style : dark header conservé, reste adouci et arrondi
 *   • Boutons  : radius 10px, ombre légère, hover smooth
 *   • Tableaux : conteneur radius 12px, lignes aérées
 *   • Champs   : radius 8px, bordure subtile
 *   • Onglets  : style pill arrondi
 * ═══════════════════════════════════════════════════════════════
 */
public final class IhmUtils
{
	// ── Palette ───────────────────────────────────────────────────────────

	public static final Color FOND     = new Color(244, 246, 250);
	public static final Color HEADER   = new Color(22,  28,  42);
	public static final Color HEADER2  = new Color(32,  40,  58);
	public static final Color SURFACE  = Color.WHITE;
	public static final Color BORD     = new Color(218, 224, 234);
	public static final Color BORD2    = new Color(195, 205, 220);
	public static final Color GRILLE   = new Color(238, 242, 248);
	public static final Color SEL      = new Color(214, 230, 255);
	public static final Color INFO     = new Color(248, 250, 253);
	public static final Color GRIS_C   = new Color(234, 238, 245);

	public static final Color BLEU     = new Color(41,  98,  225);
	public static final Color BLEU_L   = new Color(215, 228, 255);
	public static final Color VERT     = new Color(20,  155,  68);
	public static final Color VERT_L   = new Color(215, 250, 228);
	public static final Color ROUGE    = new Color(210,  38,  38);
	public static final Color ROUGE_L  = new Color(255, 222, 222);
	public static final Color AMBER    = new Color(200, 112,   6);
	public static final Color AMBER_L  = new Color(255, 240, 195);

	public static final Color TEXTE    = new Color(24,  30,  46);
	public static final Color MUTED    = new Color(95, 108, 130);

	public static final Color GREEN_LIVE = new Color(52, 211, 153);
	public static final Color RED_LIVE   = new Color(220, 38, 38);

	public static final String FONT_NAME = "Segoe UI";

	private IhmUtils() {}

	// ══════════════════════════════════════════════════════════════════════
	//  BOUTONS arrondis avec ombre + hover
	// ══════════════════════════════════════════════════════════════════════

	public static JButton bouton(String texte, Color bg, Color fg)
	{
		JButton b = new JButton(texte)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				// Ombre douce
				if (isEnabled()) {
					g2.setColor(new Color(0, 0, 0, 18));
					g2.fill(new RoundRectangle2D.Float(2, 3, getWidth()-2, getHeight()-1, 12, 12));
				}
				// Fond bouton
				Color c = !isEnabled()
					? new Color(190, 194, 200)
					: getModel().isPressed()
						? bg.darker().darker()
						: getModel().isRollover()
							? bg.darker()
							: bg;
				g2.setColor(c);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth()-1, getHeight()-2, 12, 12));
				g2.dispose();
				super.paintComponent(g);
			}
			@Override
			protected void paintBorder(Graphics g) { /* pas de bordure carrée */ }
		};
		b.setForeground(fg);
		b.setFont(new Font("SansSerif", Font.BOLD, 12));
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setContentAreaFilled(false);
		b.setOpaque(false);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setBorder(new EmptyBorder(8, 18, 10, 18));
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		return b;
	}

	public static JButton boutonCompact(String texte, Color bg, Color fg)
	{
		JButton b = bouton(texte, bg, fg);
		b.setBorder(new EmptyBorder(5, 14, 7, 14));
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		b.setFont(new Font(FONT_NAME, Font.PLAIN, 12));
		return b;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CHAMPS DE FORMULAIRE arrondis
	// ══════════════════════════════════════════════════════════════════════

	public static JTextField champTexte(String defaut)
	{
		JTextField tf = new JTextField(defaut) {
			@Override protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
				g2.dispose();
				super.paintComponent(g);
			}
		};
		tf.setFont(new Font(FONT_NAME, Font.PLAIN, 13));
		tf.setBackground(SURFACE);
		tf.setForeground(TEXTE);
		tf.setOpaque(false);
		tf.setBorder(BorderFactory.createCompoundBorder(
			new RoundBorder(BORD, 8),
			new EmptyBorder(5, 10, 5, 10)));
		tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		return tf;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  TABLEAUX
	// ══════════════════════════════════════════════════════════════════════

	public static JTable creerTable(DefaultTableModel model)
	{
		JTable t = new JTable(model)
		{
			@Override
			public Component prepareRenderer(TableCellRenderer r, int row, int col)
			{
				Component c = super.prepareRenderer(r, row, col);
				if (!isRowSelected(row))
				{
					Color bg = c.getBackground();
					boolean estCouleurDefaut =
						bg == null
						|| bg.equals(Color.WHITE)
						|| bg.equals(new Color(249, 251, 254));

					if (estCouleurDefaut)
						c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(249, 251, 254));
				}
				else
					c.setBackground(SEL);

				if (c instanceof JComponent)
					((JComponent) c).setBorder(new EmptyBorder(0, 8, 0, 8));
				return c;
			}
		};
		t.setRowHeight(30);
		t.setFont(new Font(FONT_NAME, Font.PLAIN, 13));
		t.setShowVerticalLines(false);
		t.setShowHorizontalLines(true);
		t.setGridColor(new Color(235, 240, 248));
		t.setIntercellSpacing(new Dimension(0, 0));
		t.setFillsViewportHeight(true);
		t.setSelectionBackground(SEL);
		t.setSelectionForeground(TEXTE);

		JTableHeader header = t.getTableHeader();
		header.setFont(new Font(FONT_NAME, Font.BOLD, 12));
		header.setBackground(GRIS_C);
		header.setForeground(MUTED);
		header.setReorderingAllowed(false);
		header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BORD));
		header.setPreferredSize(new Dimension(0, 34));
		return t;
	}

	/**
	 * Enveloppe un JScrollPane de table dans un conteneur aux coins arrondis.
	 */
	public static JPanel enveloperTable(JTable table)
	{
		JScrollPane scroll = new JScrollPane(table);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(Color.WHITE);

		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(Color.WHITE);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
				g2.dispose();
			}
			@Override protected void paintBorder(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(BORD);
				g2.draw(new RoundRectangle2D.Float(0, 0, getWidth()-1, getHeight()-1, 14, 14));
				g2.dispose();
			}
		};
		wrapper.setOpaque(false);
		wrapper.add(scroll);
		// Clip arrondi pour le scroll
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(true);
		return wrapper;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  PANELS
	// ══════════════════════════════════════════════════════════════════════

	public static JPanel panelBlanc(int padding)
	{
		JPanel p = new JPanel() {
			@Override protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(SURFACE);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
				g2.dispose();
			}
			@Override protected void paintBorder(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(BORD);
				g2.draw(new RoundRectangle2D.Float(0, 0, getWidth()-1, getHeight()-1, 14, 14));
				g2.dispose();
			}
		};
		p.setOpaque(false);
		p.setBorder(new EmptyBorder(padding, padding, padding, padding));
		return p;
	}

	public static JPanel panelFormulaire(int largeur)
	{
		JPanel p = panelBlanc(16);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		if (largeur > 0) p.setPreferredSize(new Dimension(largeur, 0));
		return p;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HEADER DARK
	// ══════════════════════════════════════════════════════════════════════

	public static JPanel creerHeaderDark(String titre, String sousTitre)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(HEADER);
		p.setBorder(new EmptyBorder(14, 22, 14, 22));

		JPanel textes = new JPanel();
		textes.setLayout(new BoxLayout(textes, BoxLayout.Y_AXIS));
		textes.setOpaque(false);

		JLabel lblTitre = new JLabel(titre);
		lblTitre.setFont(new Font(FONT_NAME, Font.BOLD, 15));
		lblTitre.setForeground(Color.WHITE);
		textes.add(lblTitre);

		if (sousTitre != null && !sousTitre.isEmpty())
		{
			JLabel lblSous = new JLabel(sousTitre);
			lblSous.setFont(new Font(FONT_NAME, Font.PLAIN, 11));
			lblSous.setForeground(new Color(145, 160, 185));
			textes.add(lblSous);
		}
		p.add(textes, BorderLayout.WEST);

		// Trait bas subtil
		JPanel bas = new JPanel();
		bas.setBackground(new Color(45, 58, 82));
		bas.setPreferredSize(new Dimension(0, 1));
		p.add(bas, BorderLayout.SOUTH);
		return p;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  LABELS & SÉPARATEURS
	// ══════════════════════════════════════════════════════════════════════

	public static JLabel labelSection(String texte)
	{
		JLabel l = new JLabel(texte);
		l.setFont(new Font(FONT_NAME, Font.BOLD, 12));
		l.setForeground(MUTED);
		l.setBorder(new EmptyBorder(0, 0, 4, 0));
		return l;
	}

	public static void ajLabel(JPanel p, String texte)
	{
		JLabel l = new JLabel(texte);
		l.setFont(new Font(FONT_NAME, Font.PLAIN, 11));
		l.setForeground(MUTED);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(Box.createVerticalStrut(6));
		p.add(l);
	}

	public static JSeparator separateur()
	{
		JSeparator s = new JSeparator();
		s.setForeground(BORD);
		s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		return s;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  BADGES & CHIPS
	// ══════════════════════════════════════════════════════════════════════

	public static JLabel badge(String texte, Color bg)
	{
		JLabel l = new JLabel(texte) {
			@Override protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(bg);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
				g2.dispose();
				super.paintComponent(g);
			}
		};
		l.setFont(new Font(FONT_NAME, Font.BOLD, 10));
		l.setOpaque(false);
		l.setForeground(Color.WHITE);
		l.setBorder(new EmptyBorder(2, 8, 2, 8));
		return l;
	}

	public static JPanel chip(String titre, String valeur, Color couleurVal, Color bgChip)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(bgChip);
		p.setBorder(new EmptyBorder(6, 14, 6, 14));

		JLabel lTitre = new JLabel(titre);
		lTitre.setFont(new Font(FONT_NAME, Font.PLAIN, 9));
		lTitre.setForeground(new Color(148, 163, 184));
		lTitre.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel lVal = new JLabel(valeur);
		lVal.setFont(new Font(FONT_NAME, Font.BOLD, 15));
		lVal.setForeground(couleurVal);
		lVal.setAlignmentX(Component.CENTER_ALIGNMENT);

		p.add(lTitre);
		p.add(lVal);
		return p;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  UTILITAIRES
	// ══════════════════════════════════════════════════════════════════════

	public static String fmt(double h) { return String.format("%.1fh", h); }

	public static JPanel pastille(Color c, String label)
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		p.setBackground(FOND);
		JPanel sq = new JPanel() {
			@Override protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(c);
				g2.fillOval(0, 0, getWidth()-1, getHeight()-1);
				g2.dispose();
			}
		};
		sq.setOpaque(false);
		sq.setPreferredSize(new Dimension(12, 12));
		JLabel l = new JLabel(label);
		l.setFont(new Font(FONT_NAME, Font.PLAIN, 11));
		l.setForeground(TEXTE);
		p.add(sq); p.add(l);
		return p;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  BORDURE ARRONDIE (helper interne)
	// ══════════════════════════════════════════════════════════════════════

	public static class RoundBorder extends AbstractBorder
	{
		private final Color color;
		private final int   radius;
		public RoundBorder(Color color, int radius) { this.color = color; this.radius = radius; }

		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int w, int h)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			g2.draw(new RoundRectangle2D.Float(x, y, w-1, h-1, radius, radius));
			g2.dispose();
		}

		@Override
		public Insets getBorderInsets(Component c) { return new Insets(1,1,1,1); }
		@Override
		public Insets getBorderInsets(Component c, Insets i)
		{ i.set(1,1,1,1); return i; }
	}
}
