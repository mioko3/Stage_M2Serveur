package app.ihm.serveur;

import app.ServeurHTTP;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Onglet "Semaine suivante" — UNIQUEMENT import Excel + état + bouton bascule.
 * La pré-affectation est dans PanelAffectationServeur (onglet dédié).
 */
public class PanelSemaineSuivante extends JPanel
{
	private static final Color C_BG      = new Color(246, 248, 251);
	private static final Color C_SURFACE = Color.WHITE;
	private static final Color C_BORDER  = new Color(220, 226, 235);
	private static final Color C_TEXT    = new Color(26,  32,  44);
	private static final Color C_MUTED   = new Color(100, 112, 132);
	private static final Color C_BLUE    = new Color(37,  99,  235);
	private static final Color C_GREEN   = new Color(22,  163,  74);
	private static final Color C_RED     = new Color(220,  38,  38);

	private final ServeurHTTP            serveur;
	private final PanelAffectationServeur panelAff;  // référence pour recharger après import

	// ── Widgets import ────────────────────────────────────────────────────
	private JButton btnImport;
	private JLabel  lblFichier;
	private JPanel  panelPreview;
	private JLabel  lblPreviewRes;
	private ArrayList<Lot> lotsImportTemp = null;

	// ── Widgets état ──────────────────────────────────────────────────────
	private JLabel  lblEtat;
	private JButton btnBasculer;

	// ═════════════════════════════════════════════════════════════════════
	//  CONSTRUCTION
	// ═════════════════════════════════════════════════════════════════════

	public PanelSemaineSuivante(ServeurHTTP serveur, PanelAffectationServeur panelAff)
	{
		this.serveur  = serveur;
		this.panelAff = panelAff;
		setLayout(new BorderLayout());
		setBackground(C_BG);

		JScrollPane scroll = new JScrollPane(construireContenu());
		scroll.setBorder(null);
		scroll.getViewport().setBackground(C_BG);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		chargerEtat();
	}

	// ── Ancienne signature sans panelAff (compatibilité) ──────────────────
	public PanelSemaineSuivante(ServeurHTTP serveur)
	{
		this(serveur, null);
	}

	private JPanel construireContenu()
	{
		JPanel root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(C_BG);
		root.setBorder(new EmptyBorder(20, 24, 20, 24));

		root.add(construireHeader());
		root.add(Box.createRigidArea(new Dimension(0, 12)));
		root.add(construireCarteImport());
		root.add(Box.createRigidArea(new Dimension(0, 16)));
		root.add(construireConsigne());

		return root;
	}

	// ── Header : état + bouton bascule ────────────────────────────────────

	private JPanel construireHeader()
	{
		JPanel card = buildCarte();
		card.setLayout(new BorderLayout(12, 0));

		lblEtat = new JLabel("Chargement…");
		lblEtat.setFont(new Font("SansSerif", Font.PLAIN, 13));
		lblEtat.setForeground(C_TEXT);

		btnBasculer = buildBtn("⚡ Basculer vers la semaine suivante", new Color(180, 100, 20));
		btnBasculer.setVisible(false);
		btnBasculer.addActionListener(e -> basculerSemaine());

		card.add(lblEtat,     BorderLayout.CENTER);
		card.add(btnBasculer, BorderLayout.EAST);
		return card;
	}

	// ── Carte import Excel ────────────────────────────────────────────────

	private JPanel construireCarteImport()
	{
		JPanel carte = buildCarte();

		JLabel titre = new JLabel("Import Excel — semaine suivante");
		titre.setFont(new Font("SansSerif", Font.BOLD, 13));
		titre.setForeground(C_TEXT);
		titre.setAlignmentX(Component.LEFT_ALIGNMENT);
		carte.add(titre);
		carte.add(Box.createRigidArea(new Dimension(0, 10)));

		JPanel rowF = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		rowF.setOpaque(false);
		btnImport = buildBtn("📂 Choisir un fichier Excel", C_BLUE);
		btnImport.addActionListener(e -> choisirFichier());
		lblFichier = new JLabel("Aucun fichier sélectionné");
		lblFichier.setForeground(C_MUTED);
		lblFichier.setFont(new Font("SansSerif", Font.PLAIN, 12));
		rowF.add(btnImport);
		rowF.add(lblFichier);
		carte.add(rowF);
		carte.add(Box.createRigidArea(new Dimension(0, 10)));

		// Prévisualisation (masquée jusqu'à lecture)
		panelPreview = new JPanel();
		panelPreview.setLayout(new BoxLayout(panelPreview, BoxLayout.Y_AXIS));
		panelPreview.setOpaque(false);
		panelPreview.setVisible(false);

		lblPreviewRes = new JLabel();
		lblPreviewRes.setForeground(C_GREEN);
		lblPreviewRes.setFont(new Font("SansSerif", Font.BOLD, 13));
		lblPreviewRes.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel rowBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		rowBtns.setOpaque(false);
		JButton btnConfirmer = buildBtn("✓ Enregistrer et préparer", C_GREEN);
		btnConfirmer.addActionListener(e -> confirmerImport());
		JButton btnAnnuler = buildBtn("✕ Annuler", C_SURFACE);
		btnAnnuler.setForeground(C_TEXT);
		btnAnnuler.setBorder(BorderFactory.createLineBorder(C_BORDER));
		btnAnnuler.addActionListener(e -> annulerImport());
		rowBtns.add(btnConfirmer);
		rowBtns.add(btnAnnuler);

		panelPreview.add(lblPreviewRes);
		panelPreview.add(Box.createRigidArea(new Dimension(0, 8)));
		panelPreview.add(rowBtns);
		carte.add(panelPreview);

		return carte;
	}

	// ── Consigne : rediriger vers l'onglet affectation ────────────────────

	private JPanel construireConsigne()
	{
		JPanel carte = buildCarte();

		JLabel ico = new JLabel("ℹ");
		ico.setFont(new Font("SansSerif", Font.BOLD, 18));
		ico.setForeground(C_BLUE);

		JLabel texte = new JLabel("<html>"
			+ "<b>Étapes :</b><br><br>"
			+ "1. Choisir un fichier Excel et cliquer <b>« Enregistrer et préparer »</b><br>"
			+ "2. Aller dans l'onglet <b>« Pré-affectation »</b> pour affecter les lots aux sociétés/ACE<br>"
			+ "3. Revenir ici et cliquer <b>« Basculer »</b> pour activer la nouvelle semaine sur tous les clients"
			+ "</html>");
		texte.setFont(new Font("SansSerif", Font.PLAIN, 12));
		texte.setForeground(C_TEXT);

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		row.setOpaque(false);
		row.add(ico);
		row.add(texte);

		carte.add(row);
		return carte;
	}

	// ═════════════════════════════════════════════════════════════════════
	//  LOGIQUE IMPORT
	// ═════════════════════════════════════════════════════════════════════

	private void choisirFichier()
	{
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
			"Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

		java.io.File f = fc.getSelectedFile();
		lblFichier.setText(f.getName());
		lblFichier.setForeground(C_TEXT);
		panelPreview.setVisible(false);
		lotsImportTemp = null;
		btnImport.setEnabled(false);

		new Thread(() -> {
			try {
				ArrayList<Lot> lots = serveur.lireExcelPourSemaineSuivante(f.getAbsolutePath());
				SwingUtilities.invokeLater(() -> {
					lotsImportTemp = lots;
					lblPreviewRes.setText("✓ " + lots.size() + " lots lus depuis " + f.getName());
					panelPreview.setVisible(true);
					btnImport.setEnabled(true);
					revalidate(); repaint();
				});
			} catch (Exception ex) {
				SwingUtilities.invokeLater(() -> {
					JOptionPane.showMessageDialog(this, "Erreur lecture : " + ex.getMessage(),
						"Erreur", JOptionPane.ERROR_MESSAGE);
					annulerImport();
					btnImport.setEnabled(true);
				});
			}
		}).start();
	}

	private void confirmerImport()
	{
		if (lotsImportTemp == null) return;
		int nbLots = lotsImportTemp.size();

		ArrayList<Societe> socsPourSauv = null;
		try {
			String cheminPastouche = app.CheminApp.resoudre("app/data/pastouche/societes.json");
			java.io.File fPastouche = new java.io.File(cheminPastouche);
			if (fPastouche.exists()) {
				String jsonPastouche = new String(
					Files.readAllBytes(fPastouche.toPath()), StandardCharsets.UTF_8);
				socsPourSauv = app.metier.collecte.JsonSerialiser
					.deserialiserSocietes(jsonPastouche, lotsImportTemp);
			}
			if (socsPourSauv == null || socsPourSauv.isEmpty())
				socsPourSauv = copierSocietesVides();
		} catch (Exception ex) {
			System.err.println("[PanelSemaineSuivante] Erreur pastouche : " + ex.getMessage());
			socsPourSauv = copierSocietesVides();
		}

		serveur.sauvegarderSemaneSuivante(lotsImportTemp, socsPourSauv);
		annulerImport();
		chargerEtat();

		// Recharger aussi le panel affectation
		if (panelAff != null) panelAff.chargerDonnees();

		JOptionPane.showMessageDialog(this,
			nbLots + " lots préparés.\n"
			+ "Rendez-vous dans l'onglet « Pré-affectation » pour les affecter.",
			"Import OK", JOptionPane.INFORMATION_MESSAGE);
	}

	private void annulerImport()
	{
		lotsImportTemp = null;
		lblFichier.setText("Aucun fichier sélectionné");
		lblFichier.setForeground(C_MUTED);
		panelPreview.setVisible(false);
		lblPreviewRes.setText("");
	}

	// ═════════════════════════════════════════════════════════════════════
	//  BASCULE
	// ═════════════════════════════════════════════════════════════════════

	private void basculerSemaine()
	{
		int r = JOptionPane.showConfirmDialog(this,
			"Basculer tous les clients sur la semaine suivante ?\n"
			+ "⚠ Les données courantes (lots ET affectations) seront remplacées.",
			"Confirmer la bascule", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (r != JOptionPane.YES_OPTION) return;
		try {
			serveur.basculerSemaneSuivante();
			chargerEtat();
			if (panelAff != null) panelAff.chargerDonnees();
			JOptionPane.showMessageDialog(this,
				"Bascule effectuée. Les clients se synchroniseront dans les 3 secondes.",
				"Bascule OK", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "Erreur :\n" + ex.getMessage(),
				"Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	// ═════════════════════════════════════════════════════════════════════
	//  ÉTAT
	// ═════════════════════════════════════════════════════════════════════

	public void chargerEtat()
	{
		ArrayList<Lot>     ls = serveur.getLotsSemaneSuivante();
		ArrayList<Societe> ss = serveur.getSocietesSemaneSuivante();

		if (ls != null && !ls.isEmpty())
		{
			int nbAff = 0;
			if (ss != null) for (Societe s : ss) nbAff += s.getLots().size();
			lblEtat.setText("<html><span style='color:#16a34a'>✓ "
				+ ls.size() + " lots préparés — " + nbAff + " pré-affectés</span></html>");
			btnBasculer.setVisible(true);
		}
		else
		{
			lblEtat.setText("Aucune semaine préparée.");
			btnBasculer.setVisible(false);
		}
	}

	// ═════════════════════════════════════════════════════════════════════
	//  HELPERS
	// ═════════════════════════════════════════════════════════════════════

	private ArrayList<Societe> copierSocietesVides()
	{
		ArrayList<Societe> socs  = serveur.getSocietes();
		ArrayList<Societe> copie = new ArrayList<>();
		if (socs == null) return copie;
		for (Societe s : socs)
		{
			Societe c = new Societe(s.getNom(), s.getCe(), new ArrayList<>(), s.getTotalHeuresCE());
			for (Ace a : s.getAces())
				c.getAces().add(new Ace(a.getNom(), a.getNbPers(), a.getEffectifActuel(), 0));
			copie.add(c);
		}
		return copie;
	}

	private JPanel buildCarte()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(C_SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(C_BORDER),
			new EmptyBorder(14, 18, 14, 18)));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return p;
	}

	private JButton buildBtn(String label, Color bg)
	{
		JButton b = new JButton(label);
		b.setBackground(bg);
		b.setForeground(Color.WHITE);
		b.setFont(new Font("SansSerif", Font.BOLD, 12));
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setCursor(new Cursor(Cursor.HAND_CURSOR));
		b.setBorder(new EmptyBorder(7, 16, 7, 16));
		return b;
	}
}
