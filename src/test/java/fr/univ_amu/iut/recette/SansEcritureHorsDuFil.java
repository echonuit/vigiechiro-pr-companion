package fr.univ_amu.iut.recette;

import fr.univ_amu.iut.recette.EcrituresHorsDuFil.Population;
import fr.univ_amu.iut.recette.EcrituresHorsDuFil.Releve;
import java.util.List;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/// Fait rougir un cas quand le produit écrit dans la scène hors du fil JavaFX (#6142).
///
/// La règle : une tâche de fond ne touche aucune propriété dont dépend un nœud montré. Elle rend une
/// valeur, et c'est le fil JavaFX qui l'applique. Le défaut de #6138 la violait à chaque source
/// désignée, sous les yeux de tous les bancs de l'import, et aucun ne l'a vu.
///
/// Le relevé s'ouvre au début du cas et se ferme à sa fin, sur le patron de [SansExceptionAvalee] :
/// on relève pendant le cas et on affirme après, parce que JavaFX avale ce qu'un écouteur lève.
///
/// Ce qu'elle ne ferme pas : une tâche qui écrit après la fin de son cas ; une écriture faite par le
/// fil du banc lui-même, qui est consignée et ne fait pas rougir ; et tout ce que
/// [EcrituresHorsDuFil] déclare ne pas voir.
///
/// **Elle s'éteint d'elle-même pendant une séance filmée, et le demande avant toute chose.** La
/// question est celle de [Seance#filmee()], qui reconnaît les deux chaînes de film du dépôt. L'espion
/// n'a donc aucune clé à lui, qu'il faudrait tenir d'accord avec elles : là où l'on filme, il ne
/// charge ni son registre ni ByteBuddy, et rien ne s'ajoute à ce qu'un clip mesure.
public final class SansEcritureHorsDuFil implements BeforeEachCallback, AfterEachCallback {

    private boolean ouvert;

    @Override
    public void beforeEach(ExtensionContext contexte) {
        ouvert = !Seance.filmee();
        if (ouvert) {
            EcrituresHorsDuFil.ouvrir();
        }
    }

    @Override
    public void afterEach(ExtensionContext contexte) {
        if (!ouvert) {
            return;
        }
        ouvert = false;
        List<Releve> releves = EcrituresHorsDuFil.fermer();
        Population population = EcrituresHorsDuFil.population();
        EcrituresHorsDuFil.consigner(
                contexte.getRequiredTestClass().getName(), contexte.getDisplayName(), releves, population);
        List<Releve> duProduit =
                releves.stream().filter(releve -> !releve.parLeBanc()).toList();
        if (duProduit.isEmpty()) {
            return;
        }
        // Une écriture en entraîne d'autres sur le même nœud (le texte d'un champ déplace son
        // curseur) : on nomme le nœud de la première, et toutes les propriétés qu'il a vu écrire.
        Releve premier = duProduit.get(0);
        List<String> proprietes = duProduit.stream()
                .filter(releve -> releve.noeud().equals(premier.noeud()))
                .map(Releve::propriete)
                .distinct()
                .toList();
        long ailleurs = duProduit.stream()
                .map(Releve::noeud)
                .distinct()
                .filter(noeud -> !noeud.equals(premier.noeud()))
                .count();
        String combien = ailleurs == 0 ? "" : " (et " + ailleurs + " autre(s) nœud(s))";
        throw new AssertionError(
                "la scène a été écrite hors du fil JavaFX : " + premier.noeud() + ", propriétés « "
                        + String.join(", ", proprietes) + " »" + combien + ", depuis " + premier.fil()
                        + ", par " + premier.site() + ", sur la chaîne " + premier.chaine()
                        + ". Une tâche de fond rend une valeur, et c'est le fil JavaFX qui l'applique",
                premier.pile());
    }
}
