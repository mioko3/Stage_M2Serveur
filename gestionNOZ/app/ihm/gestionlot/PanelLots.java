package app.ihm.gestionlot;

import app.IControleur;
import app.ihm.FenetrePrincipale;
import app.ihm.IhmUtils;
import app.ihm.dialogue.DialogEditLot;
import app.metier.lot.Lot;
import app.metier.personelle.Societe;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public class PanelLots extends JPanel
{
	private final IControleur       ctrl;
	private final FenetrePrincipale fenetre;

	private DefaultTableModel  modelLots;
	private JTable             tbl;
	private JComboBox<String>  combFiltreStatut;
	private JCheckBox          chkSousDouane;
	private JTextField         txtRecherche;

	public PanelLots(IControleur ctrl, FenetrePrincipale fenetre)
	{
		this.ctrl    = ctrl;
		this.fenetre = fenetre;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(IhmUtils.FOND);

		add(creerBarre(),   BorderLayout.NORTH);
		add(creerTableau(), BorderLayout.CENTER);
	}

	private JPanel creerBarre()
	{
		combFiltreStatut = new JComboBox<>(new String[]{
			"Tous les statuts", "VA - Validé avec le CP", "BL - Bloqué", "EP - Envoi au CP"});
		combFiltreStatut.addActionListener(e -> rafraichir());

		chkSousDouane = new JCheckBox("Inclure les lots sous douane");
		chkSousDouane.setBackground(IhmUtils.FOND);
		chkSousDouane.addActionListener(e -> rafraichir());

		txtRecherche = new JTextField(20);
		txtRecherche.setToolTipText("Recherche : N° CDE, typologie, affaire...");
		txtRecherche.addKeyListener(new KeyAdapter()
		{
			public void keyReleased(KeyEvent e) { rafraichir(); }
		});

		JButton btnEdit  = IhmUtils.bouton("✏ Modifier",  IhmUtils.BLEU,          Color.WHITE);
		JButton btnSuppr = IhmUtils.bouton("🗑 Supprimer", new Color(180, 30, 30), Color.WHITE);
		btnEdit .setMaximumSize(new Dimension(110, 28));
		btnSuppr.setMaximumSize(new Dimension(110, 28));
		btnEdit .addActionListener(e -> ouvrirEdition());
		btnSuppr.addActionListener(e -> supprimerLot());

		JLabel hint = new JLabel("  double-clic pour modifier");
		hint.setFont(new Font("SansSerif", Font.ITALIC, 11));
		hint.setForeground(Color.GRAY);

		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		p.setBackground(IhmUtils.FOND);
		p.add(new JLabel("Statut :"));
		p.add(combFiltreStatut);
		p.add(Box.createHorizontalStrut(8));
		p.add(chkSousDouane);
		p.add(Box.createHorizontalStrut(16));
		p.add(new JLabel("Recherche :"));
		p.add(txtRecherche);
		p.add(Box.createHorizontalStrut(12));
		p.add(btnEdit);
		p.add(btnSuppr);
		p.add(hint);
		return p;
	}

	private JPanel creerTableau()
	{
		String[] cols = {
			"N° CDE", "Typologie", "Affaire", "Nb pièces",
			"Cadence", "Heures", "Valeur €", "Statut échant.", "Sem.",
			"Société", "Emplacement"
		};
		modelLots = new DefaultTableModel(cols, 0)
		{
			public boolean isCellEditable(int r, int c) { return false; }
		};
		tbl = IhmUtils.creerTable(modelLots);
		tbl.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2) ouvrirEdition();
			}
		});

		tbl.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer()
		{
			public Component getTableCellRendererComponent(JTable t, Object v,
					boolean sel, boolean foc, int r, int c)
			{
				super.getTableCellRendererComponent(t, v, sel, foc, r, c);
				String s = v != null ? v.toString() : "";
				if (s.startsWith("VA"))      setForeground(IhmUtils.VERT);
				else if (s.startsWith("BL")) setForeground(IhmUtils.ROUGE);
				else                         setForeground(IhmUtils.AMBER);
				if (!sel) setBackground(Color.WHITE);
				return this;
			}
		});

		tbl.setDefaultRenderer(Object.class, new DefaultTableCellRenderer()
		{
			public Component getTableCellRendererComponent(JTable t, Object v,
					boolean sel, boolean foc, int r, int c)
			{
				super.getTableCellRendererComponent(t, v, sel, foc, r, c);
				if (!sel)
				{
					Lot lot = getLotLigne(r);
					if (lot != null && lot.isEstSousDouane())
						setBackground(new Color(170, 85, 195));
					else
						setBackground(Color.WHITE);
				}
				return this;
			}
		});

		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(Color.WHITE);
		p.setBorder(BorderFactory.createLineBorder(IhmUtils.BORD));
		p.add(new JScrollPane(tbl));
		return p;
	}

	private void ouvrirEdition()
	{
		Lot lot = getLotLigne(tbl.getSelectedRow());
		if (lot == null) return;
		new DialogEditLot(fenetre, ctrl, lot, null).setVisible(true);
	}

	private void supprimerLot()
	{
		Lot lot = getLotLigne(tbl.getSelectedRow());
		if (lot == null) return;
		if (ctrl.getSocieteDuLot(lot) != null)
		{
			JOptionPane.showMessageDialog(fenetre,
				"Impossible de supprimer un lot affecté.\nRetirez d'abord l'affectation.",
				"Suppression impossible", JOptionPane.WARNING_MESSAGE);
			return;
		}
		int rep = JOptionPane.showConfirmDialog(fenetre,
			"Supprimer le lot " + lot.getNumCDE() + " ?\n" + lot.getTypologie(),
			"Confirmer", JOptionPane.YES_NO_OPTION);
		if (rep == JOptionPane.YES_OPTION)
		{
			ctrl.supprimerLot(lot);
			fenetre.rafraichirTout();
		}
	}

	private Lot getLotLigne(int row)
	{
		if (row < 0) return null;
		String  filtre          = combFiltreStatut.getSelectedIndex() > 0
			? (String) combFiltreStatut.getSelectedItem() : "";
		String  recherche       = txtRecherche.getText().toLowerCase();
		boolean inclureSousDouane = chkSousDouane != null && chkSousDouane.isSelected();
		int compteur = 0;
		for (Lot l : ctrl.getLots())
		{
			if (!inclureSousDouane && l.isEstSousDouane()) continue;
			if (!passFiltres(l, filtre, recherche)) continue;
			if (compteur == row) return l;
			compteur++;
		}
		return null;
	}

	private boolean passFiltres(Lot l, String filtre, String recherche)
	{
		if (!filtre.isEmpty()
			&& !filtre.equals(l.getStatutEchant())
			&& !filtre.equals(l.getStatut())) return false;
		if (!recherche.isEmpty())
		{
			String num  = String.valueOf(l.getNumCDE());
			String typo = s(l.getTypologie()).toLowerCase();
			String aff  = s(l.getAffaire()).toLowerCase();
			if (!num.contains(recherche) && !typo.contains(recherche)
				&& !aff.contains(recherche)) return false;
		}
		return true;
	}

	private String s(String v) { return v != null ? v : ""; }

	public void rafraichir()
	{
		modelLots.setRowCount(0);
		String filtre = combFiltreStatut != null && combFiltreStatut.getSelectedIndex() > 0
			? (String) combFiltreStatut.getSelectedItem() : "";
		String recherche = txtRecherche != null ? txtRecherche.getText().toLowerCase() : "";
		boolean inclureSousDouane = chkSousDouane != null && chkSousDouane.isSelected();

		for (Lot l : ctrl.getLots())
		{
			if (!inclureSousDouane && l.isEstSousDouane()) continue;
			if (!passFiltres(l, filtre, recherche)) continue;

			Societe soc = ctrl.getSocieteDuLot(l);
			modelLots.addRow(new Object[]{
				l.getNumCDE(),
				s(l.getTypologie()),
				s(l.getAffaire()),
				String.format("%,d", l.getNbPieces()),
				l.getCadence() > 0 ? String.format("%.2f", l.getCadence()) : "—",
				IhmUtils.fmt(l.getHeures()),
				l.getValeurVente() > 0 ? String.format("%,d €", l.getValeurVente()) : "—",
				s(l.getStatutEchant()),
				s(l.getSemaine()),
				soc != null ? soc.getNom() : "—",
				s(l.getEmplacement())
			});
		}
	}
}
