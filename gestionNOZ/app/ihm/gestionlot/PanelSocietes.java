package app.ihm.gestionlot;

import app.IControleur;
import app.ihm.FenetrePrincipale;
import app.ihm.IhmUtils;
import app.ihm.dialogue.DialogEditSociete;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public class PanelSocietes extends JPanel
{
	private final IControleur       ctrl;
	private final FenetrePrincipale fenetre;

	private DefaultTableModel modelSocietes;
	private JTable            tbl;
	private JTextArea         detailAce;

	public PanelSocietes(IControleur ctrl, FenetrePrincipale fenetre)
	{
		this.ctrl    = ctrl;
		this.fenetre = fenetre;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(IhmUtils.FOND);

		add(creerBoutons(),     BorderLayout.NORTH);
		add(creerTableau(),     BorderLayout.CENTER);
		add(creerDetailPanel(), BorderLayout.SOUTH);
	}

	private JPanel creerBoutons()
	{
		JButton btnEdit = IhmUtils.bouton("✏ Modifier la société sélectionnée", IhmUtils.BLEU, Color.WHITE);
		JButton btnNew  = new JButton("Nouvelle heure");
		btnEdit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		btnEdit.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnEdit.addActionListener(e -> ouvrirEdition());
		btnNew.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		btnNew.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnNew.addActionListener(e -> ouvrirNouvelleHeure());

		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(IhmUtils.FOND);
		p.add(btnEdit, BorderLayout.WEST);
		p.add(btnNew,  BorderLayout.EAST);
		return p;
	}

	private JPanel creerTableau()
	{
		String[] cols = {"Société", "CE", "H initiales", "H restantes", "% consommé", "Lots", "ACE"};
		modelSocietes = new DefaultTableModel(cols, 0)
		{
			public boolean isCellEditable(int r, int c) { return false; }
		};
		tbl = IhmUtils.creerTable(modelSocietes);

		tbl.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer()
		{
			public Component getTableCellRendererComponent(JTable t, Object v,
					boolean sel, boolean foc, int r, int c)
			{
				super.getTableCellRendererComponent(t, v, sel, foc, r, c);
				try
				{
					int h = Integer.parseInt(v.toString().replace("h","").trim());
					setForeground(h > 100 ? IhmUtils.VERT : h > 30 ? IhmUtils.AMBER : IhmUtils.ROUGE);
					setFont(getFont().deriveFont(Font.BOLD));
				}
				catch (NumberFormatException ex) { setForeground(Color.BLACK); }
				if (!sel) setBackground(Color.WHITE);
				return this;
			}
		});

		tbl.getSelectionModel().addListSelectionListener(e -> {
			int row = tbl.getSelectedRow();
			if (row >= 0 && row < ctrl.getSocietes().size())
				detailAce.setText(buildDetail(ctrl.getSocietes().get(row)));
		});
		tbl.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2) ouvrirEdition();
			}
		});

		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(Color.WHITE);
		p.setBorder(BorderFactory.createLineBorder(IhmUtils.BORD));
		p.add(new JScrollPane(tbl));
		return p;
	}

	private JPanel creerDetailPanel()
	{
		detailAce = new JTextArea(7, 0);
		detailAce.setEditable(false);
		detailAce.setFont(new Font("Monospaced", Font.PLAIN, 12));
		detailAce.setBackground(IhmUtils.INFO);

		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(Color.WHITE);
		p.setBorder(BorderFactory.createTitledBorder("Détail (double-clic pour modifier)"));
		p.add(new JScrollPane(detailAce));
		p.setPreferredSize(new Dimension(0, 195));
		return p;
	}

	private void ouvrirEdition()
	{
		int row = tbl.getSelectedRow();
		if (row < 0 || row >= ctrl.getSocietes().size()) return;
		new DialogEditSociete(fenetre, ctrl, ctrl.getSocietes().get(row), this).setVisible(true);
	}

	private void ouvrirNouvelleHeure()
	{
		String input = JOptionPane.showInputDialog(
			fenetre, "Entrez le numéro de semaine (1 à 53) :", JOptionPane.QUESTION_MESSAGE);

		if (input != null)
		{
			try
			{
				int semaine = Integer.parseInt(input.trim());
				if (semaine < 1 || semaine > 53) throw new NumberFormatException();
				ctrl.nouvelleHeurePourSociete(semaine);
			}
			catch (NumberFormatException ex)
			{
				JOptionPane.showMessageDialog(fenetre,
					"Saisie invalide. Veuillez entrer un nombre entre 1 et 53.",
					"Erreur", JOptionPane.ERROR_MESSAGE);
			}
		}
		this.fenetre.rafraichirTout();
	}

	private String buildDetail(Societe soc)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%-12s CE: %-20s H restantes: %dh%n%n",
			soc.getNom(), soc.getCe(), soc.getTotalHeuresCE()));
		sb.append("ACE :\n");
		for (Ace a : soc.getAces())
			sb.append(String.format("  • %-20s %2d pers. (eff: %d)  —  %d lots affectés%n",
				a.getNom(), a.getNbPers(), a.getEffectifActuel(), a.getLots().size()));
		if (!soc.getLots().isEmpty())
		{
			sb.append("\nLots affectés :\n");
			ArrayList<Lot> lots = new ArrayList<>(soc.getLots());
			lots.sort((l1, l2) -> {
				Ace a1 = ctrl.getAceDuLot(l1);
				Ace a2 = ctrl.getAceDuLot(l2);
				String n1 = a1 != null ? a1.getNom() : "";
				String n2 = a2 != null ? a2.getNom() : "";
				return n1.compareTo(n2);
			});
			for (Lot l : lots)
			{
				Ace a = ctrl.getAceDuLot(l);
				String typo = l.getTypologie() != null ? l.getTypologie() : "";
				if (typo.length() > 32) typo = typo.substring(0, 32) + "…";
				sb.append(String.format("  • %-10d %-34s %5.1fh  [%s]%n",
					l.getNumCDE(), typo, l.getHeures(), a != null ? a.getNom() : "—"));
			}
		}
		return sb.toString();
	}

	public void rafraichir()
	{
		int sel = tbl.getSelectedRow();
		modelSocietes.setRowCount(0);
		for (Societe soc : ctrl.getSocietes())
		{
			double consomme = soc.getLots().stream().mapToDouble(Lot::getHeures).sum();
			int init = soc.getTotalHeuresCE() + (int) Math.ceil(consomme);
			int pct  = init > 0 ? Math.round(100f * (float) consomme / init) : 0;
			modelSocietes.addRow(new Object[]{
				soc.getNom(), soc.getCe(),
				init + "h", soc.getTotalHeuresCE() + "h",
				pct + "%", soc.getLots().size(),
				soc.getAces().size() + " ACE"
			});
		}
		if (sel >= 0 && sel < modelSocietes.getRowCount())
		{
			tbl.setRowSelectionInterval(sel, sel);
			detailAce.setText(buildDetail(ctrl.getSocietes().get(sel)));
		}
		else detailAce.setText("");
	}
}