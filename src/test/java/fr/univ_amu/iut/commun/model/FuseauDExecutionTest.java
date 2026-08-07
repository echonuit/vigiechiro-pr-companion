package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/// Vérifie le **dispositif**, pas le produit : que la suite tourne bien sous le fuseau que le job
/// `fuseau-alternatif` prétend lui imposer (#3450).
///
/// ## Pourquoi ce test existe
///
/// Le job rejoue toute la suite sous `America/Cayenne` pour tenir une propriété : *ce que le produit
/// calcule pour une nuit ne dépend pas du fuseau de la machine*. Encore faut-il que le fuseau soit
/// réellement appliqué.
///
/// Il ne l'est pas par `-Duser.timezone` sur la ligne Maven : surefire fabrique ses propres JVM
/// (`forkCount=1C`), et une propriété donnée à Maven ne descend pas forcément dedans. Un job posé
/// ainsi serait **vert** avec toute la suite tournant sous le fuseau du runner - donc sans rien
/// vérifier, tout en affichant un succès. C'est le seul défaut qui se présente sous la forme d'une
/// réussite, et il n'a pas d'autre détecteur que celui-ci.
///
/// ## Pourquoi conditionnel plutôt que toujours actif
///
/// Un test qui exigerait `America/Cayenne` en dur rougirait dans le job `build` et sur tout poste de
/// développement, où le fuseau est justement celui de la machine. Le contrat est donc : *si l'on
/// annonce un fuseau, il doit être tenu*. Hors du job, `VC_FUSEAU_ATTENDU` est absente et le cas est
/// **ignoré** - ce que la suite affiche, plutôt que de le taire.
class FuseauDExecutionTest {

    /// Nom de la variable qui arme ce contrôle. Le job `fuseau-alternatif` la pose ; personne d'autre.
    private static final String VARIABLE = "VC_FUSEAU_ATTENDU";

    @Test
    @EnabledIfEnvironmentVariable(
            named = VARIABLE,
            matches = ".+",
            disabledReason = "hors du job « fuseau-alternatif » : aucun fuseau n'est annoncé, donc rien à tenir")
    @DisplayName("#3450 : quand un fuseau est annoncé à la suite, la JVM tourne réellement dessous")
    void le_fuseau_annonce_est_celui_qui_tourne() {
        String annonce = System.getenv(VARIABLE);

        assertThat(ZoneId.systemDefault().getId())
                .as(
                        "le job annonce « %s », mais la JVM des tests tourne sous un autre fuseau : "
                                + "toute la suite s'exécuterait alors sans vérifier ce qu'elle prétend vérifier",
                        annonce)
                .isEqualTo(annonce);
    }
}
