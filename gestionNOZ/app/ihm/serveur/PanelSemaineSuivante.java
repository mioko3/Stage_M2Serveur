package app.ihm.serveur;

import app.ServeurHTTP;
import app.ihm.IhmUtils;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

/**
 * ══════════════════════════════════════════════════════════════════════
 *  PanelSemaineSuivante
 *
 *  Layout : BorderLayout principal
 *    NORTH  → header (état + bouton bascule)
 *    CENTER → JTabbedPane
 *               ├── Onglet 1 : "📦 Lots & Affectation"
 *               │     carte import + PanelAffectationServeur
 *               └── Onglet 2 : "🕒 Sociétés & Heures"
 *                     édition heures/ACE des sociétés préparées
 * ══════════════════════════════════════════════════════════════════════
 */
public class PanelSemaineSuivante extends JPanel
{
	// ── Palette ──────────────────────────────────────────────────────────
	private static final Color C_BG      = new Color(246, 248, 251);
	private static final Color C_SURFACE = Color.WHITE;
	private static final Color C_BORDER  = new Color(220, 226, 235);
	private static final Color C_TEXT    = new Color(26,  32,  44);
	private static final Color C_MUTED   = new Color(100, 112, 132);
	private static final Color C_BLUE    = new Color(37,  99,  235);
	private static final Color C_GREEN   = new Color(22,  163,  74);
	private static final Color C_RED     = new Color(220,  38,  38);
	private static final Color C_ORANGE  = new Color(180, 100,  20);

	private final ServeurHTTP serveur;

	// ── État import ───────────────────────────────────────────────────────
	private ArrayList<Lot> lotsImportTemp  = null;
	private String         cheminExcelTemp = null;

	// ── Cache mémoire des sociétés préparées ─────────────────────────────
	private ArrayList<Societe> socsPrepEnMemoire = null;

	// ── Header ────────────────────────────────────────────────────────────
	private JLabel  lblEtat;
	private JButton btnBasculer;

	// ── Import (onglet 1) ─────────────────────────────────────────────────
	private JButton btnImport;
	private JLabel  lblFichier;
	private JPanel  panelPreview;
	private JLabel  lblPreviewRes;

	// ── Onglets ───────────────────────────────────────────────────────────
	private JTabbedPane             tabs;
	private PanelAffectationServeur panelAffServeur;

	// ── Onglet 2 — Sociétés & Heures ─────────────────────────────────────
	private JComboBox<String> combSocEdit;
	private JTextField        fHeures;
	private JTextField        fEffectif;
	private DefaultTableModel mdlAces;
	private JTable            tblAces;
	private JLabel            lblInfoSoc;

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTEUR
	// ══════════════════════════════════════════════════════════════════════

	public PanelSemaineSuivante(ServeurHTTP serveur)
	{
		this.serveur = serveur;
		setLayout(new BorderLayout(0, 0));
		setBackground(C_BG);

		add(construireHeader(), BorderLayout.NORTH);
		add(construireTabs(),   BorderLayout.CENTER);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HEADER
	// ══════════════════════════════════════════════════════════════════════

	private JPanel construireHeader()
	{
		JPanel p = new JPanel(new BorderLayout(12, 0));
		p.setBackground(C_SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER),
			new EmptyBorder(10, 20, 10, 20)));

		lblEtat = new JLabel("Chargement…");
		lblEtat.setFont(new Font("SansSerif", Font.PLAIN, 13));
		lblEtat.setForeground(C_TEXT);

		btnBasculer = buildBtn("⚡ Basculer vers la semaine suivante", C_ORANGE);
		btnBasculer.setVisible(false);
		btnBasculer.addActionListener(e -> basculerSemaine());

		p.add(lblEtat,     BorderLayout.CENTER);
		p.add(btnBasculer, BorderLayout.EAST);
		return p;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  ONGLETS
	// ══════════════════════════════════════════════════════════════════════

	private JTabbedPane construireTabs()
	{
		tabs = new JTabbedPane(JTabbedPane.TOP);
		tabs.setBackground(C_BG);
		tabs.setFont(new Font("SansSerif", Font.PLAIN, 13));

		tabs.addTab("  📦 Lots & Affectation  ", construireOnglet1());
		tabs.addTab("  🕒 Sociétés & Heures  ",  construireOnglet2());

		tabs.addChangeListener(e -> {
			if (tabs.getSelectedIndex() == 1)
				rafraichirOngletSocietes();
		});

		return tabs;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  ONGLET 1 — LOTS & AFFECTATION
	// ══════════════════════════════════════════════════════════════════════

	private JPanel construireOnglet1()
	{
		JPanel root = new JPanel(new BorderLayout(0, 0));
		root.setBackground(C_BG);

		// ── Carte import en haut ──────────────────────────────────────────
		JPanel carteImport = new JPanel();
		carteImport.setLayout(new BoxLayout(carteImport, BoxLayout.Y_AXIS));
		carteImport.setBackground(C_SURFACE);
		carteImport.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER),
			new EmptyBorder(12, 20, 12, 20)));

		JLabel titreSec = new JLabel("Import Excel — semaine suivante");
		titreSec.setFont(new Font("SansSerif", Font.BOLD, 13));
		titreSec.setForeground(C_TEXT);
		titreSec.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel rowF = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		rowF.setOpaque(false);
		btnImport = buildBtn("📂 Choisir un fichier Excel", C_BLUE);
		btnImport.addActionListener(e -> choisirFichier());
		lblFichier = new JLabel("Aucun fichier sélectionné");
		lblFichier.setFont(new Font("SansSerif", Font.PLAIN, 12));
		lblFichier.setForeground(C_MUTED);
		rowF.add(btnImport);
		rowF.add(lblFichier);

		// Prévisualisation
		panelPreview = new JPanel();
		panelPreview.setLayout(new BoxLayout(panelPreview, BoxLayout.Y_AXIS));
		panelPreview.setOpaque(false);
		panelPreview.setAlignmentX(Component.LEFT_ALIGNMENT);
		panelPreview.setVisible(false);

		lblPreviewRes = new JLabel();
		lblPreviewRes.setForeground(C_GREEN);
		lblPreviewRes.setFont(new Font("SansSerif", Font.BOLD, 13));
		lblPreviewRes.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel rowBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		rowBtns.setOpaque(false);
		JButton btnOk  = buildBtn("✓ Enregistrer et préparer", C_GREEN);
		JButton btnNon = buildBtn("✕ Annuler", C_RED);
		btnOk .addActionListener(e -> confirmerImport());
		btnNon.addActionListener(e -> annulerImport());
		rowBtns.add(btnOk);
		rowBtns.add(btnNon);

		panelPreview.add(lblPreviewRes);
		panelPreview.add(Box.createRigidArea(new Dimension(0, 4)));
		panelPreview.add(rowBtns);

		carteImport.add(titreSec);
		carteImport.add(Box.createRigidArea(new Dimension(0, 8)));
		carteImport.add(rowF);
		carteImport.add(Box.createRigidArea(new Dimension(0, 6)));
		carteImport.add(panelPreview);

		// ── PanelAffectationServeur remplit le reste ──────────────────────
		panelAffServeur = new PanelAffectationServeur(serveur);

		root.add(carteImport,    BorderLayout.NORTH);
		root.add(panelAffServeur, BorderLayout.CENTER);
		return root;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  ONGLET 2 — SOCIÉTÉS & HEURES
	// ══════════════════════════════════════════════════════════════════════

	private JScrollPane construireOnglet2()
	{
		JPanel root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(C_BG);
		root.setBorder(new EmptyBorder(16, 20, 16, 20));

		// Sélecteur société
		JPanel rowSel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		rowSel.setOpaque(false);
		rowSel.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel lblSel = new JLabel("Société :");
		lblSel.setFont(new Font("SansSerif", Font.BOLD, 13));
		combSocEdit = new JComboBox<>();
		combSocEdit.setPreferredSize(new Dimension(220, 30));
		combSocEdit.setFont(new Font("SansSerif", Font.PLAIN, 13));
		combSocEdit.addActionListener(e -> afficherSocieteSelectionnee());
		rowSel.add(lblSel);
		rowSel.add(combSocEdit);

		lblInfoSoc = new JLabel(" ");
		lblInfoSoc.setFont(new Font("SansSerif", Font.PLAIN, 12));
		lblInfoSoc.setForeground(C_MUTED);
		lblInfoSoc.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Carte heures & effectif
		JPanel carteH = buildCarte("Budget heures & effectif");
		JPanel form = new JPanel(new GridBagLayout());
		form.setOpaque(false);
		form.setAlignmentX(Component.LEFT_ALIGNMENT);
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(5, 6, 5, 6);
		gc.fill = GridBagConstraints.HORIZONTAL;

		fHeures   = new JTextField(10);
		fEffectif = new JTextField(10);

		gc.gridx=0; gc.gridy=0; gc.weightx=0.3; form.add(lblForm("Heures disponibles (CE)"), gc);
		gc.gridx=1; gc.weightx=0.7; form.add(fHeures, gc);
		gc.gridx=0; gc.gridy=1; gc.weightx=0.3; form.add(lblForm("Effectif total"), gc);
		gc.gridx=1; gc.weightx=0.7; form.add(fEffectif, gc);

		JButton btnSaveH = buildBtn("💾 Enregistrer", C_BLUE);
		btnSaveH.addActionListener(e -> sauvegarderHeuresSociete());
		JPanel rowSH = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
		rowSH.setOpaque(false);
		rowSH.setAlignmentX(Component.LEFT_ALIGNMENT);
		rowSH.add(btnSaveH);

		carteH.add(form);
		carteH.add(rowSH);

		// Carte ACE
		JPanel carteA = buildCarte("ACE — modifiables dans le tableau");
		mdlAces = new DefaultTableModel(new String[]{"Nom ACE", "Nb personnes", "Effectif actuel"}, 0)
		{ public boolean isCellEditable(int r, int c) { return true; } };
		tblAces = IhmUtils.creerTable(mdlAces);
		tblAces.setRowHeight(26);

		JScrollPane scrollAce = new JScrollPane(tblAces);
		scrollAce.setPreferredSize(new Dimension(580, 150));
		scrollAce.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
		scrollAce.setAlignmentX(Component.LEFT_ALIGNMENT);
		scrollAce.setBorder(BorderFactory.createLineBorder(C_BORDER));

		JButton btnAdd   = IhmUtils.bouton("+ Ajouter ACE",    new Color(60, 140, 60), Color.WHITE);
		JButton btnDel   = IhmUtils.bouton("− Supprimer",      new Color(180, 30, 30), Color.WHITE);
		JButton btnSaveA = buildBtn("💾 Enregistrer ACE", C_BLUE);
		btnAdd .addActionListener(e -> mdlAces.addRow(new Object[]{"Nouvelle ACE", 1, 1}));
		btnDel .addActionListener(e -> { int r = tblAces.getSelectedRow(); if (r >= 0) mdlAces.removeRow(r); });
		btnSaveA.addActionListener(e -> sauvegarderAcesSociete());

		JPanel barreA = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		barreA.setOpaque(false);
		barreA.setAlignmentX(Component.LEFT_ALIGNMENT);
		barreA.add(btnAdd); barreA.add(btnDel); barreA.add(btnSaveA);

		carteA.add(scrollAce);
		carteA.add(barreA);

		// Assemblage
		root.add(rowSel);
		root.add(Box.createRigidArea(new Dimension(0, 4)));
		root.add(lblInfoSoc);
		root.add(Box.createRigidArea(new Dimension(0, 14)));
		root.add(carteH);
		root.add(Box.createRigidArea(new Dimension(0, 12)));
		root.add(carteA);
		root.add(Box.createVerticalGlue());

		JScrollPane scroll = new JScrollPane(root);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(C_BG);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  LOGIQUE — ÉTAT
	// ══════════════════════════════════════════════════════════════════════

	public void chargerEtat()
	{
		ArrayList<Lot>     ls = serveur.getLotsSemaneSuivante();
		ArrayList<Societe> ss = serveur.getSocietesSemaneSuivante();

		if (ls != null && !ls.isEmpty())
		{
			lblEtat.setText("<html><span style='color:#26a85a'>✓ "
				+ ls.size() + " lots préparés.</span></html>");
			btnBasculer.setVisible(true);
			panelAffServeur.chargerDonnees();
			if (tabs.getSelectedIndex() == 1)
				rafraichirOngletSocietes();
		}
		else
		{
			lblEtat.setText("Aucune semaine préparée — importez un fichier Excel.");
			btnBasculer.setVisible(false);
			panelAffServeur.chargerDonnees(); // nettoie
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  LOGIQUE — ONGLET 2 SOCIÉTÉS
	// ══════════════════════════════════════════════════════════════════════

	private void rafraichirOngletSocietes()
	{
		if (combSocEdit == null) return;
		ArrayList<Societe> socs = serveur.getSocietesSemaneSuivante();
		socsPrepEnMemoire = socs;
		String ancien = combSocEdit.getSelectedItem() != null
			? combSocEdit.getSelectedItem().toString() : null;
		combSocEdit.removeAllItems();

		if (socs == null || socs.isEmpty())
		{
			lblInfoSoc.setText("<html><span style='color:#e55'>Aucune société préparée.</span></html>");
			fHeures.setText(""); fEffectif.setText(""); mdlAces.setRowCount(0);
			return;
		}
		for (Societe s : socs) combSocEdit.addItem(s.getNom());
		if (ancien != null) combSocEdit.setSelectedItem(ancien);
		afficherSocieteSelectionnee();
	}

	private void afficherSocieteSelectionnee()
	{
		Societe soc = getSocPrepSelectionnee();
		if (soc == null) return;

		fHeures.setText(String.valueOf(soc.getTotalHeuresCE()));
		fEffectif.setText(String.valueOf(soc.getEffectifTotal()));

		mdlAces.setRowCount(0);
		for (Ace a : soc.getAces())
			mdlAces.addRow(new Object[]{a.getNom(), a.getNbPers(), a.getEffectifActuel()});

		int nbLots = soc.getLots().size();
		int hConso = 0;
		for (Lot l : soc.getLots()) hConso += (int) Math.ceil(l.getHeures());
		lblInfoSoc.setText(String.format(
			"<html><span style='color:#64748b'>%d lots affectés — %dh consommées — %dh restantes</span></html>",
			nbLots, hConso, soc.getTotalHeuresCE()));
	}

	private void sauvegarderHeuresSociete()
	{
		Societe soc = getSocPrepSelectionnee();
		if (soc == null) return;
		try
		{
			int h = Integer.parseInt(fHeures.getText().trim());
			int e = Integer.parseInt(fEffectif.getText().trim());
			soc.setTotalHeuresCE(h);
			soc.setEffectifTotal(e);
			sauvegarderPrep();
			afficherSocieteSelectionnee();
			panelAffServeur.chargerDonnees();
			JOptionPane.showMessageDialog(this,
				"Budget enregistré pour " + soc.getNom() + ".",
				"Sauvegardé", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (NumberFormatException ex)
		{
			JOptionPane.showMessageDialog(this,
				"Valeur invalide : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void sauvegarderAcesSociete()
	{
		Societe soc = getSocPrepSelectionnee();
		if (soc == null) return;
		if (tblAces.isEditing()) tblAces.getCellEditor().stopCellEditing();

		ArrayList<Ace> nouvellesAces = new ArrayList<>();
		for (int i = 0; i < mdlAces.getRowCount(); i++)
		{
			String nom      = mdlAces.getValueAt(i, 0).toString().trim();
			int    nbPers   = parseIntSafe(mdlAces.getValueAt(i, 1));
			int    effectif = parseIntSafe(mdlAces.getValueAt(i, 2));
			Ace a = new Ace(nom, nbPers, effectif, effectif);
			Ace ancienne = soc.getAce(nom);
			if (ancienne != null) a.getLots().addAll(ancienne.getLots());
			nouvellesAces.add(a);
		}
		soc.setAces(nouvellesAces);
		sauvegarderPrep();
		afficherSocieteSelectionnee();
		panelAffServeur.chargerDonnees();
		JOptionPane.showMessageDialog(this,
			"ACE enregistrées pour " + soc.getNom() + ".",
			"Sauvegardé", JOptionPane.INFORMATION_MESSAGE);
	}

	private void sauvegarderPrep()
	{
		// NE PAS relire depuis le serveur — on passe les listes déjà modifiées en mémoire
		ArrayList<Lot>     lots = serveur.getLotsSemaneSuivante();
		ArrayList<Societe> socs = getSocietesEnMemoire();
		if (lots == null || socs == null) return;
		try { serveur.sauvegarderSemaneSuivante(lots, socs); }
		catch (Exception ex)
		{ System.err.println("[PanelSemaineSuivante] Erreur sauvegarde : " + ex.getMessage()); }
	}

	private Societe getSocPrepSelectionnee()
	{
		if (combSocEdit == null || socsPrepEnMemoire == null) return null;
		String nom = combSocEdit.getSelectedItem() != null
			? combSocEdit.getSelectedItem().toString() : null;
		if (nom == null) return null;
		for (Societe s : socsPrepEnMemoire) if (s.getNom().equals(nom)) return s;
		return null;
	}

	private ArrayList<Societe> getSocietesEnMemoire()
	{
		return socsPrepEnMemoire;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  LOGIQUE — IMPORT
	// ══════════════════════════════════════════════════════════════════════

	private void choisirFichier()
	{
		javax.swing.filechooser.FileNameExtensionFilter filtre =
			new javax.swing.filechooser.FileNameExtensionFilter(
				"Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm");
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(filtre);
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

		java.io.File f = fc.getSelectedFile();
		lblFichier.setText(f.getName());
		lblFichier.setForeground(C_TEXT);
		panelPreview.setVisible(false);
		lotsImportTemp  = null;
		cheminExcelTemp = f.getAbsolutePath();

		btnImport.setEnabled(false);
		new Thread(() -> {
			try
			{
				ArrayList<Lot> lots = serveur.lireExcelPourSemaineSuivante(f.getAbsolutePath());
				SwingUtilities.invokeLater(() -> {
					lotsImportTemp = lots;
					lblPreviewRes.setText("✓ " + lots.size() + " lots lus depuis " + f.getName());
					panelPreview.setVisible(true);
					btnImport.setEnabled(true);
					revalidate(); repaint();
				});
			}
			catch (Exception ex)
			{
				SwingUtilities.invokeLater(() -> {
					JOptionPane.showMessageDialog(PanelSemaineSuivante.this,
						"Erreur lecture : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
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

		try
		{
			String cheminP = app.CheminApp.resoudre("app/data/pastouche/societes.json");
			java.io.File fP = new java.io.File(cheminP);
			if (fP.exists())
			{
				String json = new String(Files.readAllBytes(fP.toPath()), StandardCharsets.UTF_8);
				socsPourSauv = app.metier.collecte.JsonSerialiser
					.deserialiserSocietes(json, lotsImportTemp);
			}
			if (socsPourSauv == null || socsPourSauv.isEmpty())
				socsPourSauv = copierSocietesVides();
		}
		catch (Exception ex)
		{
			System.err.println("[PanelSemaineSuivante] Erreur pastouche : " + ex.getMessage());
			socsPourSauv = copierSocietesVides();
		}

		serveur.sauvegarderSemaneSuivante(lotsImportTemp, socsPourSauv);
		annulerImport();
		chargerEtat();

		JOptionPane.showMessageDialog(this,
			nbLots + " lots préparés.\nVous pouvez pré-affecter les lots et ajuster "
			+ "les heures dans les onglets ci-dessous.",
			"Semaine préparée", JOptionPane.INFORMATION_MESSAGE);
	}

	private void annulerImport()
	{
		lotsImportTemp  = null;
		cheminExcelTemp = null;
		lblFichier.setText("Aucun fichier sélectionné");
		lblFichier.setForeground(C_MUTED);
		panelPreview.setVisible(false);
		lblPreviewRes.setText("");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  LOGIQUE — BASCULE
	// ══════════════════════════════════════════════════════════════════════

	private void basculerSemaine()
	{
		int r = JOptionPane.showConfirmDialog(this,
			"Basculer tous les clients sur la semaine suivante ?\n"
			+ "⚠ Les données courantes seront remplacées.",
			"Confirmer la bascule", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (r != JOptionPane.YES_OPTION) return;
		try
		{
			serveur.basculerSemaneSuivante();
			chargerEtat();
			JOptionPane.showMessageDialog(this,
				"Bascule effectuée. Les clients se synchroniseront dans les 3 secondes.",
				"Bascule OK", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this,
				"Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HELPERS
	// ══════════════════════════════════════════════════════════════════════

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

	private int parseIntSafe(Object v)
	{
		try { return Integer.parseInt(v.toString().trim()); }
		catch (Exception e) { return 0; }
	}

	private JLabel lblForm(String texte)
	{
		JLabel l = new JLabel(texte);
		l.setFont(new Font("SansSerif", Font.PLAIN, 12));
		l.setForeground(C_MUTED);
		return l;
	}

	private JPanel buildCarte(String titre)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(C_SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(C_BORDER), titre),
			new EmptyBorder(8, 14, 10, 14)));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return p;
	}

	private JButton buildBtn(String texte, Color fond)
	{
		JButton b = new JButton(texte)
		{
			@Override protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getModel().isRollover() ? fond.darker() : fond);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
				g2.dispose();
				super.paintComponent(g);
			}
		};
		b.setForeground(Color.WHITE);
		b.setFont(new Font("SansSerif", Font.BOLD, 12));
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setContentAreaFilled(false);
		b.setOpaque(false);
		b.setBorder(new EmptyBorder(7, 16, 7, 16));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}
}