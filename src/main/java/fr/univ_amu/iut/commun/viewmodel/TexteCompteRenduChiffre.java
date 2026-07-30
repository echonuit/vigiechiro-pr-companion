package fr.univ_amu.iut.commun.viewmodel;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Rend un [CompteRenduChiffre] **en texte**, quand la surface ne sait pas dessiner de barre.
///
/// Jumeau de [TexteCompteRendu], qui fait le même office pour un [CompteRendu] : même contenu, autre
/// médium. Il existe pour une raison précise et étroite - le port
/// [fr.univ_amu.iut.commun.view.Notificateur] a appris à transporter un compte rendu chiffré (#2757),
/// mais une implémentation qui n'affiche que du texte ne doit pas pour autant **cesser de rendre
/// compte**. C'est le repli de sa méthode par défaut.
///
/// Ce qu'il perd est exactement ce que la barre apportait : la proportion **vue**. Il garde donc les
/// nombres et les pourcentages sous forme lisible, pour que le repli reste honnête plutôt que muet.
public final class TexteCompteRenduChiffre {

    /// Marqueurs de sévérité, alignés sur ceux de [TexteCompteRendu] : les deux surfaces textuelles
    /// balisent leurs lignes de la même façon.
    private static final Map<Severite, String> MARQUEUR = Map.of(
            Severite.SUCCES, "[ok]",
            Severite.INFO, "[i]",
            Severite.AVERTISSEMENT, "[!]",
            Severite.ERREUR, "[X]");

    private static final String RETRAIT = "    ";

    private TexteCompteRenduChiffre() {}

    public static String rendre(CompteRenduChiffre rendu) {
        List<String> lignes = new ArrayList<>();
        lignes.add(rendu.titre() + " - " + rendu.resultat());
        for (Segment part : rendu.ventilation().segments()) {
            lignes.add(RETRAIT + part.libelle() + " : " + part.valeurLisible() + pourcentage(part, rendu));
        }
        for (Motif motif : rendu.motifs()) {
            lignes.add(motif.libelle() + " (" + motif.sujets().size() + ")");
            for (String sujet : motif.sujets()) {
                lignes.add(RETRAIT + sujet);
            }
        }
        for (Avertissement avis : rendu.avertissements()) {
            lignes.add(MARQUEUR.get(avis.severite()) + " " + avis.texte());
        }
        return String.join("\n", lignes);
    }

    /// La part, en pourcentage du total. C'est la seule chose que le texte peut offrir à la place d'une
    /// barre : un rapport que le lecteur n'a pas à calculer.
    private static String pourcentage(Segment part, CompteRenduChiffre rendu) {
        long total = rendu.ventilation().total();
        if (total <= 0) {
            return "";
        }
        return String.format(" (%.1f %%)", 100.0 * part.quantite() / total);
    }
}
