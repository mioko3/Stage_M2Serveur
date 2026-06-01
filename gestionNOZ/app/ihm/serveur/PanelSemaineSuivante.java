package app.ihm.serveur;

import app.ServeurHTTP;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

/**
 * ══════════════════════════════════════════════════════════════
 *  PanelSemaineSuivante
 *
 *  1. Import Excel → prévisualiser les lots de la semaine suivante
 *  2. Enregistrer les lots préparés (sans écraser la semaine courante)
 *  3. Interface de pré-affectation : 3 colonnes (dispo / action / affectés)
 *  4. Bascule : écrase la semaine courante + affectations préparées
 * ══════════════════════════════════════════════════════════════
 */
public class PanelSemaineSuivante extends JPanel
{
	// ── Palette ───────────────────────────────────────────────────────────
	private static final Color C_BG      = new Color(15, 17, 26);
	private static final Color C_SURFACE = new Color(24, 27, 40);
	private static final Color C_CARD    = new Color(30, 34, 50);
	private static final Color C_BORDER  = new Color(50, 55, 78);
	private static final Color C_TEXT    = new Color(215, 220, 235);
	private static final Color C_MUTED   = new Color(120, 128, 155);
	private static final Color C_BLUE    = new Color(64, 128, 230);
	private static final Color C_GREEN   = new Color(38, 168, 90);
	private static final Color C_RED     = new Color(210, 65, 65);
	private static final Color C_ACCENT  = new Color(100, 160, 255);

	private final ServeurHTTP serveur;

	// ── État ──────────────────────────────────────────────────────────────
	private ArrayList<Lot>     lotsPrep     = null;
	private ArrayList<Societe> socsPrepCopy = null;
	private Lot                lotSel       = null;
	private ArrayList<Lot>     lotsImportTemp = null; // lots lus depuis Excel, avant confirmation
	private String             cheminExcelTemp = null; // chemin du fichier Excel pour lire sociétés

	// ── Composants header ────────────────────────────────────────────────
	private JLabel   lblEtat;
	private JButton  btnBasculer;

	// ── Composants import ────────────────────────────────────────────────
	private JButton  btnImport;
	private JLabel   lblFichier;
	private JPanel   panelPreview;
	private JLabel   lblPreviewRes;

	// ── Composants pré-affectation ────────────────────────────────────────
	private JPanel            panelAff;
	private JTable            tblDisp;
	private DefaultTableModel mdlDisp;
	private JTable            tblAff;
	private DefaultTableModel mdlAff;
	private JComboBox<String> combSoc;
	private JComboBox<String> combAce;
	private JTextArea         txtInfoLot;

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTEUR
	// ══════════════════════════════════════════════════════════════════════

	public PanelSemaineSuivante(ServeurHTTP serveur)
	{
		this.serveur = serveur;
		setLayout(new BorderLayout());
		setBackground(C_BG);
		setBorder(new EmptyBorder(14, 16, 14, 16));

		JScrollPane scroll = new JScrollPane(construireContenu());
		scroll.setBorder(null);
		scroll.getViewport().setBackground(C_BG);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		add(scroll, BorderLayout.CENTER);

		chargerEtat();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTION UI
	// ══════════════════════════════════════════════════════════════════════

	private JPanel construireContenu()
	{
		JPanel root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(C_BG);

		// ── Carte état ────────────────────────────────────────────────────
		JPanel carteEtat = buildCard();
		carteEtat.add(buildTitre("📅 Préparer la semaine suivante"));
		carteEtat.add(Box.createRigidArea(new Dimension(0, 4)));
		carteEtat.add(buildDesc(
			"Importez le fichier Excel puis pré-affectez les lots. "
			+ "À la bascule, tout le contenu actuel est remplacé."));
		carteEtat.add(Box.createRigidArea(new Dimension(0, 10)));

		lblEtat = new JLabel("Aucune semaine préparée.");
		lblEtat.setForeground(C_MUTED);
		lblEtat.setFont(new Font("SansSerif", Font.PLAIN, 13));
		lblEtat.setAlignmentX(Component.LEFT_ALIGNMENT);
		carteEtat.add(lblEtat);
		carteEtat.add(Box.createRigidArea(new Dimension(0, 10)));

		btnBasculer = buildBtn("⚡ Basculer tous les clients sur cette semaine", C_RED);
		btnBasculer.setVisible(false);
		btnBasculer.addActionListener(e -> basculerSemaine());
		carteEtat.add(btnBasculer);

		root.add(carteEtat);
		root.add(Box.createRigidArea(new Dimension(0, 12)));

		// ── Carte import ──────────────────────────────────────────────────
		JPanel carteImport = buildCard();
		carteImport.add(buildTitre("📊 Importer un fichier Excel de lots"));
		carteImport.add(Box.createRigidArea(new Dimension(0, 8)));

		JPanel rowF = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		rowF.setOpaque(false);
		rowF.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		btnImport = buildBtn("📁 Choisir…", C_BLUE);
		btnImport.addActionListener(e -> choisirFichier());
		lblFichier = new JLabel("Aucun fichier sélectionné");
		lblFichier.setForeground(C_MUTED);
		lblFichier.setFont(new Font("SansSerif", Font.PLAIN, 12));
		rowF.add(btnImport);
		rowF.add(lblFichier);
		carteImport.add(rowF);
		carteImport.add(Box.createRigidArea(new Dimension(0, 8)));

		// Panneau de prévisualisation (caché jusqu'à la lecture)
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
		btnAnnuler.addActionListener(e -> annulerImport());
		rowBtns.add(btnConfirmer);
		rowBtns.add(btnAnnuler);

		panelPreview.add(lblPreviewRes);
		panelPreview.add(Box.createRigidArea(new Dimension(0, 8)));
		panelPreview.add(rowBtns);
		carteImport.add(panelPreview);

		root.add(carteImport);
		root.add(Box.createRigidArea(new Dimension(0, 12)));

		// ── Zone pré-affectation (ajoutée dynamiquement après import) ─────
		panelAff = new JPanel();
		panelAff.setLayout(new BoxLayout(panelAff, BoxLayout.Y_AXIS));
		panelAff.setOpaque(false);
		panelAff.setVisible(false);
		root.add(panelAff);

		return root;
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
			lotsPrep     = ls;
			socsPrepCopy = (ss != null) ? ss : copierSocietesVides();
			int nbAff    = compterAffectes(socsPrepCopy);

			lblEtat.setText("<html><span style='color:#26a85a'>✓ "
				+ lotsPrep.size() + " lots préparés — " + nbAff
				+ " pré-affectés.</span></html>");
			btnBasculer.setVisible(true);

			construireZoneAffectation();
			panelAff.setVisible(true);
			
			// Forcer le repaint du parent (ScrollPane)
			SwingUtilities.invokeLater(() -> {
				PanelSemaineSuivante.this.revalidate();
				PanelSemaineSuivante.this.repaint();
			});
		}
		else
		{
			lotsPrep     = null;
			socsPrepCopy = null;
			lblEtat.setText("Aucune semaine préparée.");
			btnBasculer.setVisible(false);
			panelAff.setVisible(false);
		}
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
		lotsImportTemp = null;
		cheminExcelTemp = f.getAbsolutePath(); // Sauvegarder le chemin

		btnImport.setEnabled(false);
		new Thread(() ->
		{
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
		// CORRECTION BUG : garder la taille AVANT d'appeler annulerImport()
		if (lotsImportTemp == null) return;
		int nbLots = lotsImportTemp.size();

		ArrayList<Societe> socsPourSauv = null;

		// Charger les sociétés depuis le template pastouche
		try {
			String cheminPastouche = app.CheminApp.resoudre("app/data/pastouche/societes.json");
			socsPourSauv = app.metier.collecte.JsonSerialiser.deserialiserSocietes(cheminPastouche, lotsImportTemp);
			if (socsPourSauv == null) socsPourSauv = new ArrayList<>();
		} catch (Exception ex) {
			// Dernier recours : Societe vides
			socsPourSauv = copierSocietesVides();
		}

		serveur.sauvegarderSemaneSuivante(lotsImportTemp, socsPourSauv);

		// annulerImport() remet lotsImportTemp = null → appeler APRÈS avoir sauvegardé la taille
		annulerImport();
		chargerEtat();

		JOptionPane.showMessageDialog(this,
			nbLots + " lots préparés pour la semaine suivante.",
			"Semaine préparée", JOptionPane.INFORMATION_MESSAGE);
	}

	private void annulerImport()
	{
		lotsImportTemp = null;
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
			+ "⚠ Les données courantes (lots ET affectations) seront remplacées.",
			"Confirmer la bascule", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (r != JOptionPane.YES_OPTION) return;

		try
		{
			serveur.basculerSemaneSuivante();
			chargerEtat();
			JOptionPane.showMessageDialog(this,
				"Bascule effectuée. Tous les clients se synchroniseront dans les 3 secondes.",
				"Bascule OK", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(),
				"Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  ZONE PRÉ-AFFECTATION (3 colonnes)
	// ══════════════════════════════════════════════════════════════════════

	private void construireZoneAffectation()
	{
		panelAff.removeAll();

		// Titre de la zone
		JPanel titreCarte = new JPanel(new BorderLayout());
		titreCarte.setBackground(C_SURFACE);
		titreCarte.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(C_BORDER),
			new EmptyBorder(10, 14, 10, 14)));
		titreCarte.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
		titreCarte.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel tAff = new JLabel("🔗 Pré-affectation de la semaine suivante");
		tAff.setFont(new Font("SansSerif", Font.BOLD, 14));
		tAff.setForeground(C_TEXT);
		JLabel tSub = new JLabel("(les affectations seront actives après la bascule)");
		tSub.setFont(new Font("SansSerif", Font.PLAIN, 11));
		tSub.setForeground(C_MUTED);
		titreCarte.add(tAff, BorderLayout.WEST);
		titreCarte.add(tSub, BorderLayout.EAST);
		panelAff.add(titreCarte);
		panelAff.add(Box.createRigidArea(new Dimension(0, 8)));

		// Zone 3 colonnes — hauteur fixe
		JPanel zone = new JPanel(new GridLayout(1, 3, 10, 0));
		zone.setBackground(C_BG);
		zone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
		zone.setPreferredSize(new Dimension(0, 400));
		zone.setAlignmentX(Component.LEFT_ALIGNMENT);

		zone.add(construireColDisponibles());
		zone.add(construireColActions());
		zone.add(construireColAffectes());

		panelAff.add(zone);
		panelAff.revalidate();
		panelAff.repaint();

		rafraichirTableaux();
	}

	private JPanel construireColDisponibles()
	{
		JPanel col = new JPanel(new BorderLayout(0, 0));
		col.setBackground(C_SURFACE);
		col.setBorder(BorderFactory.createLineBorder(C_BORDER));

		JLabel titre = new JLabel("  Lots disponibles");
		titre.setFont(new Font("SansSerif", Font.BOLD, 12));
		titre.setForeground(C_TEXT);
		titre.setBackground(C_CARD);
		titre.setOpaque(true);
		titre.setBorder(new EmptyBorder(8, 8, 8, 8));
		col.add(titre, BorderLayout.NORTH);

		String[] cols = {"N°CDE", "Affaire", "H"};
		mdlDisp = new DefaultTableModel(cols, 0)
		{
			@Override public boolean isCellEditable(int r, int c) { return false; }
		};
		tblDisp = buildTable(mdlDisp);
		tblDisp.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) selectionnerLot();
		});
		col.add(new JScrollPane(tblDisp), BorderLayout.CENTER);
		return col;
	}

	private JPanel construireColActions()
	{
		JPanel col = new JPanel();
		col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
		col.setBackground(C_SURFACE);
		col.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(C_BORDER),
			new EmptyBorder(10, 10, 10, 10)));

		// Info lot sélectionné
		txtInfoLot = new JTextArea("Sélectionnez un lot");
		txtInfoLot.setEditable(false);
		txtInfoLot.setLineWrap(true);
		txtInfoLot.setWrapStyleWord(true);
		txtInfoLot.setBackground(C_CARD);
		txtInfoLot.setForeground(C_MUTED);
		txtInfoLot.setFont(new Font("SansSerif", Font.PLAIN, 11));
		txtInfoLot.setBorder(new EmptyBorder(6, 6, 6, 6));
		JScrollPane scrollInfo = new JScrollPane(txtInfoLot);
		scrollInfo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
		scrollInfo.setPreferredSize(new Dimension(0, 100));
		scrollInfo.setBorder(BorderFactory.createLineBorder(C_BORDER));
		scrollInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
		col.add(scrollInfo);
		col.add(Box.createRigidArea(new Dimension(0, 10)));

		col.add(buildLabel("Société"));
		col.add(Box.createRigidArea(new Dimension(0, 4)));
		combSoc = new JComboBox<>();
		combSoc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		combSoc.setBackground(C_CARD);
		remplirCombSoc();
		combSoc.addActionListener(e -> remplirCombAce());
		col.add(combSoc);
		col.add(Box.createRigidArea(new Dimension(0, 8)));

		col.add(buildLabel("ACE (optionnel)"));
		col.add(Box.createRigidArea(new Dimension(0, 4)));
		combAce = new JComboBox<>();
		combAce.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		combAce.setBackground(C_CARD);
		col.add(combAce);
		col.add(Box.createRigidArea(new Dimension(0, 12)));

		JButton btnAff = buildBtn("➜ Affecter", C_BLUE);
		btnAff.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnAff.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		btnAff.addActionListener(e -> affecterLot());
		col.add(btnAff);
		col.add(Box.createRigidArea(new Dimension(0, 6)));

		JButton btnRet = buildBtn("✕ Retirer", C_RED);
		btnRet.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnRet.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		btnRet.addActionListener(e -> retirerLot());
		col.add(btnRet);
		col.add(Box.createGlue());
		return col;
	}

	private JPanel construireColAffectes()
	{
		JPanel col = new JPanel(new BorderLayout(0, 0));
		col.setBackground(C_SURFACE);
		col.setBorder(BorderFactory.createLineBorder(C_BORDER));

		JLabel titre = new JLabel("  Lots pré-affectés");
		titre.setFont(new Font("SansSerif", Font.BOLD, 12));
		titre.setForeground(C_TEXT);
		titre.setBackground(C_CARD);
		titre.setOpaque(true);
		titre.setBorder(new EmptyBorder(8, 8, 8, 8));
		col.add(titre, BorderLayout.NORTH);

		String[] cols = {"N°CDE", "Affaire", "Société / ACE"};
		mdlAff = new DefaultTableModel(cols, 0)
		{
			@Override public boolean isCellEditable(int r, int c) { return false; }
		};
		tblAff = buildTable(mdlAff);
		tblAff.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) selectionnerDepuisAff();
		});
		col.add(new JScrollPane(tblAff), BorderLayout.CENTER);
		return col;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  AFFECTATION
	// ══════════════════════════════════════════════════════════════════════

	private void selectionnerLot()
	{
		int row = tblDisp.getSelectedRow();
		if (row < 0 || lotsPrep == null) { lotSel = null; majInfoLot(); return; }
		int numCDE = (Integer) mdlDisp.getValueAt(row, 0);
		lotSel = lotsPrep.stream()
			.filter(l -> l.getNumCDE() == numCDE).findFirst().orElse(null);
		majInfoLot();
	}

	private void selectionnerDepuisAff()
	{
		int row = tblAff.getSelectedRow();
		if (row < 0 || lotsPrep == null) return;
		int numCDE = (Integer) mdlAff.getValueAt(row, 0);
		lotSel = lotsPrep.stream()
			.filter(l -> l.getNumCDE() == numCDE).findFirst().orElse(null);
		majInfoLot();
		for (int i = 0; i < mdlDisp.getRowCount(); i++)
			if ((Integer) mdlDisp.getValueAt(i, 0) == numCDE)
			{ tblDisp.setRowSelectionInterval(i, i); break; }
	}

	private void majInfoLot()
	{
		if (txtInfoLot == null) return;
		if (lotSel == null) { txtInfoLot.setText("Sélectionnez un lot"); return; }
		Societe soc = getSocDuLot(lotSel);
		Ace     ace = getAceDuLot(lotSel);
		String aff = soc != null
			? soc.getNom() + (ace != null ? " / " + ace.getNom() : "")
			: "Non affecté";
		txtInfoLot.setText(
			"N° " + lotSel.getNumCDE() + "\n"
			+ trunc(lotSel.getAffaire(), 40) + "\n"
			+ "Pièces : " + lotSel.getNbPieces() + "\n"
			+ "Heures : " + String.format("%.1f", lotSel.getHeures()) + " h\n"
			+ "→ " + aff);
	}

	private void affecterLot()
	{
		if (lotSel == null)
		{ msg("Sélectionnez d'abord un lot."); return; }
		String nomSoc = (String) combSoc.getSelectedItem();
		if (nomSoc == null || nomSoc.isEmpty())
		{ msg("Choisissez une société."); return; }

		retirerDeToutes(lotSel);
		Societe soc = socsPrepCopy.stream()
			.filter(s -> s.getNom().equals(nomSoc)).findFirst().orElse(null);
		if (soc == null) return;
		soc.getLots().add(lotSel);
		String nomAce = (String) combAce.getSelectedItem();
		if (nomAce != null && !nomAce.isEmpty())
			for (Ace a : soc.getAces())
				if (a.getNom().equals(nomAce)) { a.getLots().add(lotSel); break; }

		serveur.sauvegarderSemaneSuivante(lotsPrep, socsPrepCopy);
		rafraichirTableaux();
		majInfoLot();
		majEtat();
	}

	private void retirerLot()
	{
		if (lotSel == null)
		{ msg("Sélectionnez d'abord un lot."); return; }
		retirerDeToutes(lotSel);
		serveur.sauvegarderSemaneSuivante(lotsPrep, socsPrepCopy);
		rafraichirTableaux();
		majInfoLot();
		majEtat();
	}

	private void retirerDeToutes(Lot lot)
	{
		if (lot == null || socsPrepCopy == null) return;
		for (Societe s : socsPrepCopy)
		{
			for (int i = s.getLots().size() - 1; i >= 0; i--)
			{
				Lot l = s.getLots().get(i);
				if (l != null && lot.getId() != null && lot.getId().equals(l.getId()))
					s.getLots().remove(i);
			}
			for (Ace a : s.getAces())
			{
				for (int j = a.getLots().size() - 1; j >= 0; j--)
				{
					Lot l = a.getLots().get(j);
					if (l != null && lot.getId() != null && lot.getId().equals(l.getId()))
						a.getLots().remove(j);
				}
			}
		}
	}

	private void rafraichirTableaux()
	{
		if (lotsPrep == null || socsPrepCopy == null) return;
		mdlDisp.setRowCount(0);
		for (Lot l : lotsPrep)
			mdlDisp.addRow(new Object[]{
				l.getNumCDE(),
				trunc(l.getAffaire(), 24),
				String.format("%.1f", l.getHeures())
			});
		mdlAff.setRowCount(0);
		for (Societe s : socsPrepCopy)
			for (Lot l : s.getLots()) {
				Ace ace = getAceDuLotDansSoc(l, s);
				mdlAff.addRow(new Object[]{
					l.getNumCDE(),
					trunc(l.getAffaire(), 18),
					s.getNom() + (ace != null ? " / " + ace.getNom() : "")
				});
			}
	}

	private void remplirCombSoc()
	{
		combSoc.removeAllItems();
		combSoc.addItem("");
		if (socsPrepCopy != null)
			for (Societe s : socsPrepCopy) combSoc.addItem(s.getNom());
	}

	private void remplirCombAce()
	{
		combAce.removeAllItems();
		combAce.addItem("");
		String nomSoc = (String) combSoc.getSelectedItem();
		if (nomSoc == null || nomSoc.isEmpty()) return;
		if (socsPrepCopy == null) return;
		for (Societe s : socsPrepCopy)
			if (s.getNom().equals(nomSoc))
				for (Ace a : s.getAces()) combAce.addItem(a.getNom());
	}

	private void majEtat()
	{
		if (lotsPrep == null) return;
		int n = compterAffectes(socsPrepCopy);
		lblEtat.setText("<html><span style='color:#26a85a'>✓ "
			+ lotsPrep.size() + " lots préparés — " + n + " pré-affectés.</span></html>");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HELPERS MÉTIER
	// ══════════════════════════════════════════════════════════════════════

	private ArrayList<Societe> copierSocietesVides()
	{
		ArrayList<Societe> socs = serveur.getSocietes();
		ArrayList<Societe> copie = new ArrayList<>();
		if (socs == null) return copie;
		for (Societe s : socs) {
			Societe c = new Societe(s.getNom(), s.getCe(), new ArrayList<>(), s.getTotalHeuresCE());
			for (Ace a : s.getAces())
				c.getAces().add(new Ace(a.getNom(), a.getNbPers(), a.getEffectifActuel(), 0));
			copie.add(c);
		}
		return copie;
	}

	private Societe getSocDuLot(Lot lot)
	{
		if (lot == null || socsPrepCopy == null) return null;
		for (Societe s : socsPrepCopy)
			for (Lot l : s.getLots())
				if (l != null && lot.getId() != null && lot.getId().equals(l.getId())) return s;
		return null;
	}

	private Ace getAceDuLot(Lot lot)
	{
		if (lot == null || socsPrepCopy == null) return null;
		for (Societe s : socsPrepCopy)
			for (Ace a : s.getAces())
				for (Lot l : a.getLots())
					if (l != null && lot.getId() != null && lot.getId().equals(l.getId())) return a;
		return null;
	}

	private Ace getAceDuLotDansSoc(Lot lot, Societe soc)
	{
		if (lot == null || soc == null) return null;
		for (Ace a : soc.getAces())
			for (Lot l : a.getLots())
				if (l != null && lot.getId() != null && lot.getId().equals(l.getId())) return a;
		return null;
	}

	private int compterAffectes(ArrayList<Societe> socs)
	{
		if (socs == null) return 0;
		int n = 0;
		for (Societe s : socs) n += s.getLots().size();
		return n;
	}

	private static String trunc(String s, int max)
	{
		if (s == null) return "—";
		return s.length() <= max ? s : s.substring(0, max) + "…";
	}

	private void msg(String texte)
	{
		JOptionPane.showMessageDialog(this, texte, "Info", JOptionPane.INFORMATION_MESSAGE);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HELPERS UI
	// ══════════════════════════════════════════════════════════════════════

	private JPanel buildCard()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(C_SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(C_BORDER),
			new EmptyBorder(16, 18, 16, 18)));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));
		return p;
	}

	private JLabel buildTitre(String t)
	{
		JLabel l = new JLabel(t);
		l.setFont(new Font("SansSerif", Font.BOLD, 14));
		l.setForeground(C_TEXT);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private JLabel buildDesc(String t)
	{
		JLabel l = new JLabel("<html><span style='color:#78809b'>" + t + "</span></html>");
		l.setFont(new Font("SansSerif", Font.PLAIN, 11));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private JLabel buildLabel(String t)
	{
		JLabel l = new JLabel(t);
		l.setFont(new Font("SansSerif", Font.BOLD, 11));
		l.setForeground(C_MUTED);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private JButton buildBtn(String label, Color bg)
	{
		JButton b = new JButton(label);
		b.setBackground(bg);
		b.setForeground(Color.WHITE);
		b.setFocusPainted(false);
		b.setFont(new Font("SansSerif", Font.BOLD, 12));
		b.setBorder(new EmptyBorder(7, 14, 7, 14));
		return b;
	}

	private JTable buildTable(DefaultTableModel model)
	{
		JTable t = new JTable(model);
		t.setBackground(C_SURFACE);
		t.setForeground(C_TEXT);
		t.setGridColor(C_BORDER);
		t.setSelectionBackground(new Color(64, 128, 230, 60));
		t.setSelectionForeground(C_TEXT);
		t.getTableHeader().setBackground(C_CARD);
		t.getTableHeader().setForeground(C_MUTED);
		t.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
		t.setFont(new Font("SansSerif", Font.PLAIN, 12));
		t.setRowHeight(26);
		t.setShowVerticalLines(false);
		t.setIntercellSpacing(new Dimension(10, 2));
		t.setFillsViewportHeight(true);
		return t;
	}
}