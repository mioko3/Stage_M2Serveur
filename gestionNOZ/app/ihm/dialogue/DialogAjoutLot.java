package app.ihm.dialogue;

import app.IControleur;
import app.ihm.FenetrePrincipale;
import app.ihm.IhmUtils;
import app.ihm.gestionlot.PanelAffectation;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/**
 * Dialogue modal pour ajouter un nouveau lot.
 *
 * Permet de saisir les informations administratives et logistiques
 * pour créer une nouvelle commande dans le planning.
 */
public class DialogAjoutLot extends JDialog
{
	private final IControleur       ctrl;
	private final PanelAffectation  panelAff;
	private final FenetrePrincipale fenetre;

	private JTextField        fNumCDE, fTypologie, fAffaire, fNbPieces, fCadence;
	private JTextField        fValeur, fSemaine, fLotACharge, fDateRec, fDatePai, fCommentaire;
	private JComboBox<String> fStatut, fStatutEchant;
	private JCheckBox         fDouane ,fMachine;
	private JSpinner          fPriorite;

	private JComboBox<String> fEmplacementLettre;
	private JTextField        fEmplacementNumero;

	private JLabel lblHeures, lblErreur;

	private static final Set<String> SANS_NUMERO =
		new HashSet<>(Arrays.asList("LTS", "HD", ""));

	public DialogAjoutLot(FenetrePrincipale fenetre, IControleur ctrl, PanelAffectation panelAff)
	{
		super(fenetre, "Créer un nouveau lot", true);
		this.fenetre  = fenetre;
		this.ctrl     = ctrl;
		this.panelAff = panelAff;
		setSize(540, 620);
		setLocationRelativeTo(fenetre);
		setLayout(new BorderLayout());
		add(creerFormulaire(), BorderLayout.CENTER);
		add(creerBas(),        BorderLayout.SOUTH);
	}

	private JScrollPane creerFormulaire()
	{
		fNumCDE      = new JTextField();
		fTypologie   = new JTextField();
		fAffaire     = new JTextField();
		fNbPieces    = new JTextField();
		fCadence     = new JTextField();
		fValeur      = new JTextField();
		fSemaine     = new JTextField();
		fLotACharge  = new JTextField();
		fDateRec     = new JTextField();
		fDatePai     = new JTextField();
		fCommentaire = new JTextField();
		fDouane      = new JCheckBox("Sous douane");
		fMachine     = new JCheckBox("Lot machine");
		fPriorite    = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));

		fStatut = new JComboBox<>(new String[]{"", "OU", "TC", "MR"});
		fStatutEchant = new JComboBox<>(new String[]{
			"", "VA - Validé avec le CP", "BL - Bloqué", "EP - Envoi au CP"});

		fEmplacementLettre = new JComboBox<>(new String[]{"", "A", "B", "C", "D", "LTS", "HD"});
		fEmplacementNumero = new JTextField(5);
		fEmplacementNumero.setToolTipText("Numéro de rangée (ex: 42)");
		fEmplacementNumero.setEnabled(false);

		fEmplacementLettre.addActionListener(e -> {
			String lettre = s((String) fEmplacementLettre.getSelectedItem());
			boolean avecNum = !SANS_NUMERO.contains(lettre);
			fEmplacementNumero.setEnabled(avecNum);
			if (!avecNum) fEmplacementNumero.setText("");
		});

		JPanel panelEmpl = new JPanel();
		panelEmpl.setLayout(new BoxLayout(panelEmpl, BoxLayout.X_AXIS));
		panelEmpl.setBackground(Color.WHITE);
		fEmplacementLettre.setMaximumSize(new Dimension(80, 28));
		fEmplacementNumero.setMaximumSize(new Dimension(70, 28));
		JLabel sep = new JLabel("  —  ");
		sep.setForeground(Color.GRAY);
		panelEmpl.add(fEmplacementLettre);
		panelEmpl.add(sep);
		panelEmpl.add(fEmplacementNumero);
		panelEmpl.add(Box.createHorizontalGlue());

		lblHeures = new JLabel("—");
		lblHeures.setForeground(IhmUtils.BLEU);
		lblHeures.setFont(new Font("SansSerif", Font.BOLD, 13));

		KeyAdapter majH = new KeyAdapter()
		{
			public void keyReleased(KeyEvent e) { calculerHeures(); }
		};
		fNbPieces.addKeyListener(majH);
		fCadence .addKeyListener(majH);

		Object[][] champs = {
			{"N° CDE *",         fNumCDE},
			{"Typologie *",      fTypologie},
			{"Affaire",          fAffaire},
			{"Nb pièces *",      fNbPieces},
			{"Cadence (p/h) *",  fCadence},
			{"Heures estimées",  lblHeures},
			{"Valeur vente (€)", fValeur},
			{"Statut interne",   fStatut},
			{"Statut échant.",   fStatutEchant},
			{"Semaine",          fSemaine},
			{"Priorité",         fPriorite},
			{"Lot à charge",     fLotACharge},
			{"Emplacement",      panelEmpl},
			{"Date réception",   fDateRec},
			{"Date paiement",    fDatePai},
			{"",                 fDouane},
			{"",                 fMachine},
			{"Commentaire",      fCommentaire},
		};

		JPanel form = new JPanel(new GridBagLayout());
		form.setBorder(BorderFactory.createEmptyBorder(14, 16, 8, 16));
		form.setBackground(Color.WHITE);
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(4, 4, 4, 4);
		gc.fill   = GridBagConstraints.HORIZONTAL;

		for (int i = 0; i < champs.length; i++)
		{
			gc.gridx = 0; gc.gridy = i; gc.weightx = 0.28;
			JLabel lbl = new JLabel((String) champs[i][0]);
			lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
			lbl.setForeground(Color.GRAY);
			form.add(lbl, gc);
			gc.gridx = 1; gc.weightx = 0.72;
			form.add((Component) champs[i][1], gc);
		}
		return new JScrollPane(form);
	}

	/**
	 * Crée la barre de boutons en bas du dialogue.
	 */
	private JPanel creerBas()
	{
		lblErreur = new JLabel(" ");
		lblErreur.setForeground(IhmUtils.ROUGE);
		lblErreur.setFont(new Font("SansSerif", Font.ITALIC, 12));

		JButton btnOk  = IhmUtils.bouton("Créer",   IhmUtils.BLEU,           Color.WHITE);
		JButton btnAnn = IhmUtils.bouton("Annuler", new Color(100, 100, 100), Color.WHITE);
		btnAnn.addActionListener(e -> dispose());
		btnOk .addActionListener(e -> valider());

		JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		p.setBackground(Color.WHITE);
		p.add(lblErreur); p.add(btnAnn); p.add(btnOk);
		return p;
	}

	/**
	 * Calcule et affiche le temps de production estimé du lot.
	 */
	private void calculerHeures()
	{
		try
		{
			int    nb  = Integer.parseInt(fNbPieces.getText().trim());
			double cad = Double.parseDouble(fCadence.getText().trim().replace(",", "."));
			double h   = cad > 0 ? nb / cad : 0.0;
			lblHeures.setText(String.format("%.2fh  (%.0f pièces ÷ %.2f p/h)", h, (double) nb, cad));
			lblHeures.setForeground(IhmUtils.BLEU);
		}
		catch (NumberFormatException ex)
		{
			lblHeures.setText("—");
			lblHeures.setForeground(Color.GRAY);
		}
	}

	/**
	 * Concatène l’emplacement lettre et numéro pour le lot.
	 */
	private String getEmplacementCombine()
	{
		String lettre = s((String) fEmplacementLettre.getSelectedItem());
		if (lettre.isEmpty()) return "";
		if (SANS_NUMERO.contains(lettre)) return lettre;
		return lettre + fEmplacementNumero.getText().trim();
	}

	/**
	 * Valide les champs saisis et crée le lot via le contrôleur.
	 */
	private void valider()
	{
		try
		{
			int    cde  = Integer.parseInt(fNumCDE.getText().trim());
			String typo = fTypologie.getText().trim();
			if (typo.isEmpty()) { lblErreur.setText("La typologie est obligatoire."); return; }

			int    nb  = Integer.parseInt(fNbPieces.getText().trim());
			double cad = Double.parseDouble(fCadence.getText().trim().replace(",", "."));
			double h   = cad > 0 ? nb / cad : 0.0;
			int    val = fValeur.getText().trim().isEmpty() ? 0
						: Integer.parseInt(fValeur.getText().trim());

			ctrl.ajouterLot(
				cde, typo,
				fAffaire     .getText().trim(),
				nb, cad, val,
				(String) fStatut      .getSelectedItem(),
				(String) fStatutEchant.getSelectedItem(),
				fSemaine     .getText().trim(),
				(int) fPriorite.getValue(),
				fLotACharge  .getText().trim(),
				getEmplacementCombine(),
				fDouane      .isSelected(),
				fMachine     .isSelected(),
				fDateRec     .getText().trim(),
				fDatePai     .getText().trim(),
				fCommentaire .getText().trim()
			);

			if (panelAff != null) panelAff.remplirComboSocietes();
			fenetre.rafraichirTout();
			if (panelAff != null)
				panelAff.afficherStatut("Lot " + cde + " créé (" + String.format("%.2fh", h) + ")", IhmUtils.VERT);
			dispose();
		}
		catch (NumberFormatException ex)
		{
			lblErreur.setText("Valeur numérique invalide : " + ex.getMessage());
		}
	}

	/**
	 * Retourne une chaîne vide si la valeur est nulle.
	 */
	private static String s(String v) { return v != null ? v : ""; }
}
