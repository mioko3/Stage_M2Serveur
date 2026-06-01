package app.ihm.map;

import app.metier.lot.Lot;
import java.awt.*;
import java.util.List;
import javax.swing.*;

/**
 * Bouton personnalisé représentant un emplacement sur la carte.
 *
 * Dessine une représentation colorée des lots présents dans l’emplacement.
 */
public class BoutonEmplacement extends JButton
{
	private List<Lot> lots;
	private boolean choisie;

	public BoutonEmplacement(String text, List<Lot> lots)
	{
		super(text);

		this.lots = lots;
		this.choisie = false;
		setFocusPainted(false);
		setBorderPainted(false);
		setContentAreaFilled(false);
		setOpaque(false);

		setMargin(new Insets(4, 4, 4, 4));
		setHorizontalAlignment(SwingConstants.CENTER);

		setForeground(Color.BLACK);
	}

	public void setLots(List<Lot> lots)
	{
		this.lots = lots;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();

		int w = getWidth();
		int h = getHeight();

		// Fond vide
		if (lots == null || lots.isEmpty())
		{
			g2.setColor(new Color(210, 212, 218));
			g2.fillRoundRect(0, 0, w, h, 10, 10);
		}
		else
		{
			int taille = lots.size();
			int largeurPart = Math.max(1, w / taille);

			int x = 0;

			for (int i = 0; i < taille; i++)
			{
				Lot lot = lots.get(i);

				g2.setColor(couleurLot(lot));

				if (i == taille - 1)
				{
					g2.fillRect(x, 0, w - x, h);
				}
				else
				{
					g2.fillRect(x, 0, largeurPart, h);
				}

				x += largeurPart;
			}
		}

		g2.dispose();

		super.paintComponent(g);
	}

	private Color couleurLot(Lot l)
	{
		if (choisie) return new Color(0,255,255);
		if (l.isEstSousDouane())
			return new Color(170, 85, 195);

		String st = l.getStatutEchant();

		if (st != null)
		{
			if (st.startsWith("VA"))
				return new Color(50, 150, 60);

			if (st.startsWith("BL"))
				return new Color(205, 55, 55);

			if (st.startsWith("EP"))
				return new Color(190, 115, 15);
		}

		return new Color(120, 120, 120);
	}

	public String getText() {return super.getText();}
	public void  estChoisie(boolean c) {this.choisie = c;}
}