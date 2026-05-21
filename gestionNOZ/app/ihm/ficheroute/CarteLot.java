package app.ihm.ficheroute;

import app.IControleur;
import app.ihm.IhmUtils;
import app.metier.lot.LigneColisage;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * Carte visuelle représentant un lot dans la fiche de route.
 * Version fusionnée livraison → gestionNOZ :
 *   - construireLigne3Phases(bg) sans accent (checkPhase simplifié)
 *   - construireLigne2 / construireLigneFin séparées
 *   - champ couranAce stocké
 *   - IControleur au lieu de Controleur
 */
public class CarteLot extends JPanel implements ActionListener
{
	// ── Couleurs d'état ────────────────────────────────────────────────
	static final Color BG_FINI      = new Color(220, 250, 220);
	static final Color BG_DOUANE    = new Color(238, 224, 255);
	static final Color BG_URGENCE   = new Color(255, 232, 232);
	static final Color BG_COMMENCER = new Color(250, 250, 220);
	static final Color BG_BLOQUE    = new Color(180, 180, 180);
	static final Color BG_NORMAL    = Color.WHITE;

	private static final String PRESERVE_BG = "preserve_bg";

	private static boolean estcommencer;

	private final Lot         lot;
	private final IControleur ctrl;
	private final PanelFicheRoute m;
	private       Ace         couranAce;   // ACE courante mémorisée

	// ── Champs éditables ──────────────────────────────────────────────
	private JButton           btncommencer;
	private JTextField        textPcsEtiq;
	private JTextField        textPcsPart;
	private JComboBox<String> combDistri;
	private JTextField        textLotCharge;
	private JComboBox<String> combFormCart;
	private JTextField        textCollisage;
	private JTextField        textColisRecup;
	private JTextField        textMethode;
	private JTextField        textCadenceReel;
	private JTextField        textNbPers;

	// Panel logistique (reconstruit quand on ajoute/supprime une ligne)
	private JPanel panelLogistique;

	// ── Barre de progression ───────────────────────────────────────────
	private BarreProgression barrePhasesWidget;

	// ── Panel badges état ──────────────────────────────────────────────
	private JPanel panelBadgesEtat;
	private JPanel ligne1;

	// ── Constructeur principal ─────────────────────────────────────────
	public CarteLot(Lot lot, Color color, IControleur ctrl, PanelFicheRoute m)
	{
		this.lot  = lot;
		this.ctrl = ctrl;
		this.m    = m;

		estcommencer = !lot.getDateDebut().equals("");

		Color bg     = bgPourLot(lot);
		Color accent = color != null ? color : IhmUtils.BLEU;

		setLayout(new BorderLayout());
		setBackground(bg);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEmptyBorder(3, 8, 3, 8),
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
				BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(new Color(215, 220, 228)),
					BorderFactory.createEmptyBorder(7, 10, 7, 10)
				)
			)
		));

		JPanel corps = new JPanel();
		corps.setLayout(new BoxLayout(corps, BoxLayout.Y_AXIS));
		corps.setBackground(bg);

		panelLogistique = construireLigne5Logistique(bg);

		// ── Ordre des lignes (identique à livraison) ───────────────────
		corps.add(construireLigne1(bg));
		corps.add(Box.createVerticalStrut(5));
		corps.add(separateur());
		corps.add(construireLigne2(bg));
		corps.add(separateur());
		corps.add(construireLigneFin(bg));
		corps.add(separateur());
		corps.add(construireLigneDate(bg));
		corps.add(separateur());
		corps.add(construireLigne3Phases(bg));   // sans accent — cf. livraison
		corps.add(separateur());
		corps.add(construireLigne4Avancement(bg));
		corps.add(separateur());
		corps.add(panelLogistique);
		corps.add(separateur());
		corps.add(construireCommentaire(bg));

		add(corps, BorderLayout.CENTER);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height + 16));
	}

	/** Constructeur avec ACE (mémorise couranAce). */
	public CarteLot(Lot lot, Ace ace, IControleur ctrl, PanelFicheRoute m)
	{
		this(lot, ace.getColor(), ctrl, m);
		this.couranAce = ace;
	}

	// ══════════════════════════════════════════════════════════════════
	// Lignes de contenu
	// ══════════════════════════════════════════════════════════════════

	private JPanel construireLigne1(Color bg)
	{
		ligne1 = new JPanel(new BorderLayout(6, 0));
		ligne1.setBackground(bg);

		JPanel gauche = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		gauche.setBackground(bg);

		int   prio   = lot.getPriorite();
		Color prioBg = prio >= 8 ? IhmUtils.ROUGE : prio >= 5 ? IhmUtils.AMBER : new Color(80, 130, 80);
		gauche.add(badge(" P" + prio + " ", prioBg));

		JLabel num = new JLabel("N° " + lot.getNumCDE());
		num.setFont(new Font("SansSerif", Font.BOLD, 14));
		num.setForeground(new Color(20, 55, 120));
		gauche.add(num);

		String desig    = s(lot.getAffaire());
		String typo     = s(lot.getTypologie());
		String affDesig = desig.isEmpty() ? typo : (typo.isEmpty() ? desig : desig + "  —  " + typo);
		JLabel lblDes   = new JLabel(affDesig.isEmpty() ? "(sans désignation)" : affDesig);
		lblDes.setFont(new Font("SansSerif", Font.PLAIN, 13));
		gauche.add(lblDes);

		if (!s(lot.getSemaine()).isEmpty())
		{
			JLabel sem = new JLabel("  S" + lot.getSemaine());
			sem.setFont(new Font("SansSerif", Font.PLAIN, 11));
			sem.setForeground(new Color(120, 120, 120));
			gauche.add(sem);
		}

		panelBadgesEtat = construireBadgesEtat(bg);

		ligne1.add(gauche,          BorderLayout.CENTER);
		ligne1.add(panelBadgesEtat, BorderLayout.EAST);
		return ligne1;
	}

	private JPanel construireBadgesEtat(Color bg)
	{
		JPanel droite = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		droite.setBackground(bg);
		if (lot.isEstSousDouane())              droite.add(badge("DOUANE",    new Color(120, 40, 180)));
		if (lot.getPhase().isFinit())           droite.add(badge("✓ TERMINÉ", new Color(30, 130, 50)));
		if (lot.estMachine())                   droite.add(badge("MACHINE",   new Color(0, 100, 160)));
		if (!s(lot.getStatut()).isEmpty())       droite.add(badge(lot.getStatut(), new Color(70, 70, 70)));
		if (!s(lot.getStatutEchant()).isEmpty()) droite.add(badge("Éch: " + lot.getStatutEchant(), new Color(50, 90, 160)));
		return droite;
	}

	// ── Ligne 2 : données chiffrées ───────────────────────────────────
	private JPanel construireLigne2(Color bg)
	{
		JPanel l2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
		l2.setBackground(bg);
		info(l2, "VVS",          lot.getValeurVente() > 0 ? fmt(lot.getValeurVente()) + " €" : "—", bg);
		info(l2, "Pièces",       fmt(lot.getNbPieces()), bg);
		info(l2, "PU",           lot.getPrixUnitaire() > 0 ? String.format("%.2f €", lot.getPrixUnitaire()) : "—", bg);
		info(l2, "Cadence",      lot.getCadence() > 0 ? String.format("%.0f p/h", lot.getCadence()) : "—", bg);
		info(l2, "H. Total",     lot.getHeures() > 0 ? String.format("%.1f h", lot.getHeures()) : "—", bg);
		info(l2, "H. sur piste", lot.getHeuresAce() > 0 ? String.format("%.1f h", lot.getHeuresAce()) : "—", bg);
		if (!s(lot.getEmplacement()).isEmpty())   info(l2, "Emplacement", s(lot.getEmplacement()), bg);
		if (!s(lot.getDateReception()).isEmpty()) info(l2, "Réception",   lot.getDateReception(),  bg);
		if (!s(lot.getDatePaiement()).isEmpty())  info(l2, "Paiement",    lot.getDatePaiement(),   bg);
		info(l2, "Nb de colis recup", lot.getNbColisRecup() > 0 ? String.format("%d pcs", lot.getNbColisRecup()) : "—", bg);
		return l2;
	}

	// ── Ligne Fin : durée et cadence moyenne ──────────────────────────
	private JPanel construireLigneFin(Color bg)
	{
		JPanel lFin = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
		lFin.setBackground(bg);
		if (!lot.getdateFin().isEmpty())
		{
			info(lFin, "Temps : ",       lot.calculDuree(),                               bg);
			info(lFin, "Cadence Moy : ", String.format("%.0f p/h", lot.calculCadenceMoyenne()), bg);
		}
		return lFin;
	}

	// ── Ligne Date : bouton commencer + dates ─────────────────────────
	private JPanel construireLigneDate(Color bg)
	{
		JPanel lDate = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
		lDate.setBackground(bg);
		if (!estcommencer)
		{
			this.btncommencer = new JButton("Commencer");
			this.btncommencer.addActionListener(e -> commencer());
			lDate.add(this.btncommencer);
		}
		else
		{
			this.btncommencer = new JButton("Annuler");
			this.btncommencer.addActionListener(e -> annuler());
			lDate.add(this.btncommencer);
		}
		info(lDate, "Date de Début : ",         lot.getDateDebut(), bg);
		lDate.add(separateur());
		info(lDate, "Date de Fin : ",            lot.getdateFin(),  bg);
		for (int idx = 0; idx < 5; idx++) lDate.add(separateur());
		info(lDate, "Date de Fin théorique : ",  lot.getdateFinT(), bg);
		return lDate;
	}

	private void commencer()
	{
		estcommencer = true;
		this.ctrl.commencerLot(lot);
		lot.calculDateFinThéorique();
		this.m.rafraichir();
	}

	private void annuler()
	{
		estcommencer = false;
		this.ctrl.annulerLot(lot);
		lot.setdateFinT("");
		this.m.rafraichir();
	}

	// ── Ligne 3 : phases — signature sans accent (cf. livraison) ─────
	private JPanel construireLigne3Phases(Color bg)
	{
		JPanel l3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
		l3.setBackground(bg);

		JLabel titPhases = new JLabel("Phases :");
		titPhases.setFont(new Font("SansSerif", Font.BOLD, 11));
		titPhases.setForeground(Color.GRAY);
		l3.add(titPhases);

		l3.add(checkPhase("PRÉ TRI",     lot.getPhase().isPreTri(),     "PRETRI"  ));
		l3.add(checkPhase("SUR PISTE",   lot.getPhase().isSurPiste(),   "SURPISTE"));
		l3.add(checkPhase("SORTIE ÉTIQ", lot.getPhase().isSortieEtiq(), "SORETIQ" ));
		l3.add(checkPhase("TRI",         lot.getPhase().isTri(),        "TRI"     ));
		l3.add(checkPhase("FINI",        lot.getPhase().isFinit(),      "FINI"    ));

		l3.add(Box.createHorizontalStrut(6));

		barrePhasesWidget = new BarreProgression(5);
		barrePhasesWidget.setPreferredSize(new Dimension(80, 10));
		barrePhasesWidget.setOpaque(false);
		barrePhasesWidget.mettreAJour(nbPhasesCoches());
		l3.add(barrePhasesWidget);

		return l3;
	}

	private JPanel construireLigne4Avancement(Color bg)
	{
		JPanel l4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
		l4.setBackground(bg);

		this.textPcsEtiq = new JTextField(String.valueOf(lot.getSuivieProd().getNbPieceEtiq()), 6);
		this.textPcsEtiq.setEnabled(estcommencer);
		l4.add(champEditable("Pces Étiq.", this.textPcsEtiq, bg, "PCS_ETIQ", this));
		info(l4, "Av. Étiq %",   lot.getSuivieProd().getAvancementEtiqPct(), bg);
		info(l4, "H Étiq rest.", lot.getSuivieProd().getNbHeureEtiqRestant() + " h", bg);

		JLabel sep = new JLabel("|");
		sep.setForeground(new Color(190, 190, 190));
		l4.add(sep);

		this.textPcsPart = new JTextField(String.valueOf(lot.getSuivieProd().getNbPieceRepart()), 6);
		this.textPcsPart.setEnabled(lot.getPhase().isSortieEtiq());
		l4.add(champEditable("Pces Parts", this.textPcsPart, bg, "PCS_PART", this));
		info(l4, "Av. Parts %",   lot.getSuivieProd().getAvancementPartsPct(), bg);
		info(l4, "H Parts rest.", lot.getSuivieProd().getNbHeureRepartRestant() + " h", bg);
		return l4;
	}

	// ── Logistique ────────────────────────────────────────────────────
	@SuppressWarnings("unchecked")
	private JPanel construireLigne5Logistique(Color bg)
	{
		JPanel conteneur = new JPanel();
		conteneur.setLayout(new BoxLayout(conteneur, BoxLayout.Y_AXIS));
		conteneur.setBackground(bg);

		JPanel l5 = new JPanel(new GridLayout(2, 5, 4, 2));
		l5.setBackground(bg);

		this.combFormCart    = new JComboBox(lot.F_CARTON);
		this.combFormCart.setSelectedItem(lot.getFormatCarton() == null ? "" : lot.getFormatCarton());
		this.textCollisage   = new JTextField(String.valueOf(lot.getCollisage()), 10);
		this.textNbPers      = new JTextField(String.valueOf(lot.getNbPers()), 10);
		this.combDistri      = new JComboBox(lot.DISTRI);
		this.combDistri.setSelectedItem(lot.getDistribution() == null ? "" : lot.getDistribution());
		this.textColisRecup  = new JTextField(String.valueOf(lot.getPoucentrecupCartonFour()), 10);
		this.textCadenceReel = new JTextField(String.valueOf(lot.getCadenceReel()), 10);
		this.textLotCharge   = new JTextField(s(lot.getLotACharge()), 10);
		this.textMethode     = new JTextField(lot.getMethode() == null ? "" : lot.getMethode().getNom(), 10);

		// Ligne 1
		l5.add(champEditable("Format carton",  this.combFormCart,    bg, "FORM_CART",  this));
		l5.add(champEditable("Collisage",      this.textCollisage,   bg, "COLLISAGES", this));
		l5.add(champEditable("Nombre de pers", this.textNbPers,      bg, "NBPERS",     this));
		l5.add(champEditable("Distribution",   this.combDistri,      bg, "DISTRI",     this));
		l5.add(champEditable("Colis récup. %", this.textColisRecup,  bg, "COLISRECUP", this));
		// Ligne 2
		info(l5, "Palettes",     String.valueOf(lot.getNbPalettes()),    bg);
		info(l5, "Colis prévus", String.valueOf(lot.getNbColisPrevue()), bg);
		l5.add(champEditable("Cadence Réel",   this.textCadenceReel, bg, "CADENCE",    this));
		l5.add(champEditable("Lot à charge",   this.textLotCharge,   bg, "LOT_CHARGE", this));
		l5.add(champEditable("Méthode",        this.textMethode,     bg, "METHODE",    this));

		conteneur.add(l5);

		for (int i = 0; i < lot.getLignesColisage().size(); i++)
			conteneur.add(construireRowLigneColisage(lot.getLignesColisage().get(i), i, bg));

		JButton btnAjouter = new JButton("+ format de carton supplémentaire");
		btnAjouter.setFont(new Font("SansSerif", Font.PLAIN, 11));
		btnAjouter.setForeground(IhmUtils.BLEU);
		btnAjouter.setBackground(bg);
		btnAjouter.setBorderPainted(true);
		btnAjouter.setFocusPainted(false);
		btnAjouter.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnAjouter.addActionListener(e -> ouvrirDialogueAjoutLigne());
		JPanel wrapBtn = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
		wrapBtn.setBackground(bg);
		wrapBtn.add(btnAjouter);
		conteneur.add(wrapBtn);

		return conteneur;
	}

	private JPanel construireRowLigneColisage(LigneColisage lc, int index, Color bg)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
		row.setBackground(bg);

		JLabel lbl = new JLabel(String.format(
			"  ↳ %s  ×%d  →  %d colis  /  %d palettes",
			lc.getFormatCarton(), lc.getCollisage(), lc.getNbColis(), lc.getNbPalettes()));
		lbl.setFont(new Font("Monospaced", Font.PLAIN, 11));
		lbl.setForeground(new Color(60, 60, 120));

		JButton btnSuppr = new JButton("✕");
		btnSuppr.setFont(new Font("SansSerif", Font.BOLD, 10));
		btnSuppr.setForeground(IhmUtils.ROUGE);
		btnSuppr.setBackground(bg);
		btnSuppr.setBorderPainted(false);
		btnSuppr.setFocusPainted(false);
		btnSuppr.setToolTipText("Supprimer cette ligne");
		btnSuppr.addActionListener(e -> {
			lot.supprimerLigneColisage(index);
			m.rafraichir();
		});

		row.add(lbl);
		row.add(btnSuppr);
		return row;
	}

	private JPanel construireCommentaire(Color bg)
	{
		JPanel lCom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 1));
		lCom.setBackground(bg);

		JLabel ico = new JLabel("\uD83D\uDCAC");
		ico.setFont(new Font("SansSerif", Font.PLAIN, 12));

		boolean vide = s(lot.getCommentaire()).isEmpty();
		JTextField tf = new JTextField(vide ? "Commentaire..." : lot.getCommentaire(), 42);
		tf.setFont(new Font("SansSerif", Font.ITALIC, 12));
		tf.setForeground(vide ? Color.LIGHT_GRAY : Color.DARK_GRAY);
		tf.addFocusListener(new FocusAdapter()
		{
			@Override public void focusGained(FocusEvent e)
			{
				if (tf.getText().equals("Commentaire..."))
				{ tf.setText(""); tf.setForeground(Color.DARK_GRAY); }
			}
			@Override public void focusLost(FocusEvent e)
			{
				String v = tf.getText().trim();
				if (v.isEmpty()) { tf.setText("Commentaire..."); tf.setForeground(Color.LIGHT_GRAY); }
				lot.setCommentaire(v.equals("Commentaire...") ? "" : v);
			}
		});
		tf.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(200, 210, 220)),
			BorderFactory.createEmptyBorder(2, 5, 2, 5)));

		lCom.add(ico);
		lCom.add(tf);
		return lCom;
	}

	private void ouvrirDialogueAjoutLigne()
	{
		JComboBox<String> comboFmt = new JComboBox<>(lot.F_CARTON);
		JTextField tfCol   = new JTextField("1", 5);
		JTextField tfpiece = new JTextField("1", 5);

		JPanel dlg = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		dlg.add(new JLabel("nb pieces :")); dlg.add(tfpiece);
		dlg.add(new JLabel("Format :"));   dlg.add(comboFmt);
		dlg.add(new JLabel("Collisage :")); dlg.add(tfCol);

		int res = JOptionPane.showConfirmDialog(this, dlg,
			"Ajouter un format de carton supplémentaire",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (res != JOptionPane.OK_OPTION) return;

		try
		{
			int col = Integer.parseInt(tfCol.getText().trim());
			int pcs = Integer.parseInt(tfpiece.getText().trim());
			if (col <= 0 || pcs <= 0 || pcs >= lot.getNbPieces())
				throw new NumberFormatException();
			lot.ajouterLigneColisage(new LigneColisage((String) comboFmt.getSelectedItem(), col), pcs);
			m.rafraichir();
		}
		catch (NumberFormatException ex)
		{
			JOptionPane.showMessageDialog(this, "Valeur invalide.", "Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	// ══════════════════════════════════════════════════════════════════
	// Phases — signature simplifiée sans accent/bg (cf. livraison)
	// ══════════════════════════════════════════════════════════════════

	private JCheckBox checkPhase(String label, boolean etat, String code)
	{
		JCheckBox cb = new JCheckBox(label, etat);
		cb.setFont(new Font("SansSerif", Font.PLAIN, 11));
		cb.setOpaque(false);
		cb.setForeground(etat ? new Color(20, 120, 20) : new Color(100, 100, 100));
		cb.addActionListener(e -> {
			boolean v  = cb.isSelected();
			boolean pt = lot.getPhase().isPreTri(),    sp = lot.getPhase().isSurPiste(),
			        se = lot.getPhase().isSortieEtiq(), tr = lot.getPhase().isTri(),
			        fi = lot.getPhase().isFinit();
			switch (code)
			{
				case "PRETRI":   pt = v; break;
				case "SURPISTE": sp = v; break;
				case "SORETIQ":  se = v; break;
				case "TRI":      tr = v; break;
				case "FINI":     fi = v; break;
			}
			ctrl.modifierPhase(lot, pt, sp, se, tr, fi);
			cb.setForeground(cb.isSelected() ? new Color(20, 120, 20) : new Color(100, 100, 100));
			barrePhasesWidget.mettreAJour(nbPhasesCoches());
			mettreAJourBadgesEtat();
			recolorierCarte();
			this.m.rafraichir();
		});
		return cb;
	}

	private int nbPhasesCoches()
	{
		return (lot.getPhase().isPreTri()     ? 1 : 0)
		     + (lot.getPhase().isSurPiste()   ? 1 : 0)
		     + (lot.getPhase().isSortieEtiq() ? 1 : 0)
		     + (lot.getPhase().isTri()        ? 1 : 0)
		     + (lot.getPhase().isFinit()      ? 1 : 0);
	}

	// ══════════════════════════════════════════════════════════════════
	// Recoloriage
	// ══════════════════════════════════════════════════════════════════

	private void recolorierCarte()
	{
		Color nouvBg = bgPourLot(lot);
		appliquerFond(this, nouvBg);
		revalidate();
		repaint();
	}

	private void appliquerFond(Container c, Color bg)
	{
		if (c instanceof JComponent && Boolean.TRUE.equals(((JComponent) c).getClientProperty(PRESERVE_BG)))
			return;
		c.setBackground(bg);
		for (Component child : c.getComponents())
			if (child instanceof Container) appliquerFond((Container) child, bg);
	}

	private void mettreAJourBadgesEtat()
	{
		Color bg = bgPourLot(lot);
		ligne1.remove(panelBadgesEtat);
		panelBadgesEtat = construireBadgesEtat(bg);
		ligne1.add(panelBadgesEtat, BorderLayout.EAST);
		ligne1.revalidate();
		ligne1.repaint();
	}

	// ══════════════════════════════════════════════════════════════════
	// ActionListener (champs éditables)
	// ══════════════════════════════════════════════════════════════════

	@Override
	public void actionPerformed(ActionEvent e)
	{
		String cmd = e.getActionCommand();
		try
		{
			switch (cmd)
			{
				case "PCS_ETIQ":
				{
					int v = Integer.parseInt(textPcsEtiq.getText().trim());
					if (v < 0 || v > lot.getNbPieces()) throw new NumberFormatException();
					lot.getSuivieProd().setNbPieceEtiq(v);
					textPcsEtiq.setBackground(Color.WHITE);
					break;
				}
				case "PCS_PART":
				{
					int v = Integer.parseInt(textPcsPart.getText().trim());
					if (v < 0 || v > lot.getNbPieces()) throw new NumberFormatException();
					lot.getSuivieProd().setNbPieceRepart(v);
					textPcsPart.setBackground(Color.WHITE);
					break;
				}
				case "DISTRI":
					lot.setDistribution((String) combDistri.getSelectedItem());
					break;
				case "LOT_CHARGE":
					lot.setLotACharge(textLotCharge.getText().trim());
					break;
				case "FORM_CART":
					lot.setFormatCarton((String) combFormCart.getSelectedItem());
					break;
				case "COLLISAGES":
				{
					int v = Integer.parseInt(textCollisage.getText().trim());
					if (v < 0) throw new NumberFormatException();
					lot.setCollisage(v);
					break;
				}
				case "COLISRECUP":
				{
					int v = Integer.parseInt(textColisRecup.getText().trim());
					if (v >= 0 && v <= 100) lot.setPoucentrecupCartonFour(v);
					break;
				}
				case "METHODE":
					lot.setMethode(textMethode.getText().trim());
					break;
				case "CADENCE":
				{
					double v = Double.parseDouble(textCadenceReel.getText().trim());
					if (v > 0) lot.setCadenceReel(v);
					break;
				}
				case "NBPERS":
				{
					int v = Integer.parseInt(textNbPers.getText().trim());
					if (v > 0) lot.setNbPers(v);
					break;
				}
			}
			this.m.rafraichir();
		}
		catch (NumberFormatException ex)
		{
			if (e.getSource() instanceof JTextField)
				((JTextField) e.getSource()).setBackground(new Color(255, 220, 220));
		}
	}

	// ══════════════════════════════════════════════════════════════════
	// Helpers visuels statiques
	// ══════════════════════════════════════════════════════════════════

	static JLabel badge(String txt, Color col)
	{
		JLabel l = new JLabel(txt);
		l.setFont(new Font("SansSerif", Font.BOLD, 10));
		l.setOpaque(true);
		l.setBackground(col);
		l.setForeground(Color.WHITE);
		l.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
		l.putClientProperty(PRESERVE_BG, Boolean.TRUE);
		return l;
	}

	static void info(JPanel p, String label, String val, Color bg)
	{
		JPanel bloc = new JPanel(new BorderLayout(0, 0));
		bloc.setBackground(bg);
		JLabel l = new JLabel(label);
		l.setFont(new Font("SansSerif", Font.PLAIN, 10));
		l.setForeground(new Color(130, 130, 130));
		JLabel v = new JLabel(val);
		v.setFont(new Font("SansSerif", Font.BOLD, 12));
		v.setForeground(Color.DARK_GRAY);
		bloc.add(l, BorderLayout.NORTH);
		bloc.add(v, BorderLayout.CENTER);
		p.add(bloc);
	}

	static JSeparator separateur()
	{
		JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
		sep.setForeground(new Color(220, 225, 232));
		sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		return sep;
	}

	static JPanel champEditable(String label, JTextField t, Color bg, String action, ActionListener listener)
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		p.setBackground(bg);
		JLabel l = new JLabel(label + " :");
		l.setFont(new Font("SansSerif", Font.BOLD, 11));
		l.setForeground(new Color(90, 90, 90));
		t.setFont(new Font("SansSerif", Font.PLAIN, 12));
		t.setActionCommand(action);
		t.addActionListener(listener);
		p.add(l);
		p.add(t);
		return p;
	}

	static JPanel champEditable(String label, JComboBox<String> t, Color bg, String action, ActionListener listener)
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		p.setBackground(bg);
		JLabel l = new JLabel(label + " :");
		l.setFont(new Font("SansSerif", Font.BOLD, 11));
		l.setForeground(new Color(90, 90, 90));
		t.setActionCommand(action);
		t.addActionListener(listener);
		p.add(l);
		p.add(t);
		return p;
	}

	// ── Barre de progression ───────────────────────────────────────────

	private static class BarreProgression extends JPanel
	{
		private final int max;
		private       int valeur;

		BarreProgression(int max)
		{
			super(null);
			this.max    = max;
			this.valeur = 0;
			setOpaque(false);
		}

		void mettreAJour(int nouvValeur)
		{
			this.valeur = nouvValeur;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int W = getWidth(), H = getHeight();
			g2.setColor(new Color(215, 220, 228));
			g2.fillRoundRect(0, 0, W, H, H, H);
			int fill = max > 0 ? (int)(W * valeur / (double) max) : 0;
			if (fill > 0)
			{
				Color c = valeur == max ? IhmUtils.VERT : IhmUtils.AMBER;
				g2.setColor(c);
				g2.fillRoundRect(0, 0, fill, H, H, H);
			}
		}
	}

	// ── Utilitaires ───────────────────────────────────────────────────

	static Color bgPourLot(Lot lot)
	{
		if (lot.getPhase().isFinit())             return BG_FINI;
		if (lot.isEstSousDouane())                return BG_DOUANE;
		if (lot.getPriorite() >= 8)               return BG_URGENCE;
		if (estcommencer)                         return BG_COMMENCER;
		if (lot.getStatutEchant().contains("BL")) return BG_BLOQUE;
		return BG_NORMAL;
	}

	private static String s(String v)  { return v != null ? v : ""; }
	private static String fmt(int n)   { return String.format("%,d", n); }
}