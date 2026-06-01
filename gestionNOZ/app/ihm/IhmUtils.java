package app.ihm;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** Constantes de couleurs et fabriques de composants partagées entre tous les panneaux. */
public final class IhmUtils
{
	public static final Color VERT   = new Color(34, 139, 34);
	public static final Color ROUGE  = new Color(180, 30, 30);
	public static final Color AMBER  = new Color(180, 110, 0);
	public static final Color FOND   = new Color(248, 249, 250);
	public static final Color HEADER = new Color(30, 30, 30);
	public static final Color BORD   = new Color(220, 220, 220);
	public static final Color GRIS_C = new Color(240, 241, 243);
	public static final Color SEL    = new Color(210, 225, 245);
	public static final Color GRILLE = new Color(230, 230, 230);
	public static final Color INFO   = new Color(245, 246, 248);
	public static final Color BLEU   = new Color(0, 100, 160);

	private IhmUtils() {}

	public static JButton bouton(String texte, Color bg, Color fg)
	{
		JButton b = new JButton(texte);
		b.setBackground(bg); b.setForeground(fg);
		b.setFocusPainted(false); b.setBorderPainted(false);
		b.setFont(new Font("SansSerif", Font.PLAIN, 13));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		return b;
	}

	public static JLabel labelSection(String texte)
	{
		JLabel l = new JLabel(texte);
		l.setFont(new Font("SansSerif", Font.BOLD, 13));
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		return l;
	}

	public static JSeparator separateur()
	{
		JSeparator s = new JSeparator();
		s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		return s;
	}

	public static void ajLabel(JPanel p, String texte)
	{
		JLabel l = new JLabel(texte);
		l.setFont(new Font("SansSerif", Font.PLAIN, 11));
		l.setForeground(Color.GRAY);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(Box.createVerticalStrut(4));
		p.add(l);
	}

	public static JTable creerTable(DefaultTableModel model)
	{
		JTable t = new JTable(model);
		t.setRowHeight(26);
		t.setFont(new Font("SansSerif", Font.PLAIN, 13));
		t.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
		t.getTableHeader().setBackground(GRIS_C);
		t.getTableHeader().setReorderingAllowed(false);
		t.setSelectionBackground(SEL);
		t.setGridColor(GRILLE);
		t.setIntercellSpacing(new Dimension(8, 4));
		return t;
	}

	public static JPanel panelBlanc(int padding)
	{
		JPanel p = new JPanel();
		p.setBackground(Color.WHITE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORD),
			BorderFactory.createEmptyBorder(padding, padding, padding, padding)));
		return p;
	}

	public static JPanel panelFormulaire(int largeur)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		if (largeur > 0) p.setPreferredSize(new Dimension(largeur, 0));
		p.setBackground(Color.WHITE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORD),
			BorderFactory.createEmptyBorder(14, 14, 14, 14)));
		return p;
	}

	/** Formate les heures avec 1 décimale. */
	public static String fmt(double h)
	{
		return String.format("%.1fh", h);
	}
}
