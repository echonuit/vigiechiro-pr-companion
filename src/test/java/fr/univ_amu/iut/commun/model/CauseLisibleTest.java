package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le message qu'une alerte d'incident doit montrer (#3470).
class CauseLisibleTest {

    @Test
    @DisplayName("#3470 : l'enveloppe de réflexion ne masque plus la panne qu'elle transporte")
    void l_enveloppe_de_reflexion_ne_masque_pas_la_panne() {
        // La chaîne exacte du retour de terrain. FXML invoque un `onAction="#methode"` par réflexion :
        // ce qui est levé dedans part en InvocationTargetException, puis en RuntimeException(cette
        // enveloppe). Or `new RuntimeException(cause)` pose comme message `cause.toString()`, d'où le
        // « java.lang.reflect.InvocationTargetException » affiché à l'utilisateur.
        IOException panne = new IOException("Accès refusé : E:\\");
        RuntimeException telleQueFxmlLaRelance = new RuntimeException(new InvocationTargetException(panne));

        // Le message n'était pas absent : il était exact et inutile. C'est pourquoi rien ne rougissait.
        assertThat(telleQueFxmlLaRelance.getMessage()).isEqualTo("java.lang.reflect.InvocationTargetException");

        assertThat(CauseLisible.messageDe(telleQueFxmlLaRelance)).isEqualTo("Accès refusé : E:\\");
    }

    @Test
    @DisplayName("On retient le message le plus profond QUI PARLE, pas la cause la plus profonde")
    void le_plus_profond_qui_parle_et_non_le_plus_profond() {
        // La nuance décide du résultat : la cause racine est ici un NullPointerException muet, moins
        // parlant que son parent. « Dérouler jusqu'au bout » rendrait donc l'alerte plus pauvre
        // qu'avant, tout en ayant l'air de la corriger.
        IOException parlante = new IOException("Le journal du capteur est illisible", new NullPointerException());

        assertThat(CauseLisible.messageDe(parlante)).isEqualTo("Le journal du capteur est illisible");
    }

    @Test
    @DisplayName("Le défaut n'est pas la réflexion : toute enveloppe (Throwable) fabrique le même texte")
    void le_defaut_n_est_pas_propre_a_la_reflexion() {
        // Trouvé par un rouge inattendu en écrivant cette classe. `RuntimeException(Throwable)` - comme
        // tous les constructeurs (Throwable) de la JDK - pose comme message `cause.toString()`.
        // « InvocationTargetException » n'était donc pas un cas particulier de la réflexion : c'est
        // cette convention-là, et elle produit le même texte inutile derrière n'importe quelle
        // enveloppe. Une règle « non vide, donc informatif » aurait remplacé un nom de classe par un
        // autre, en ayant l'air de corriger.
        RuntimeException enveloppeOrdinaire = new RuntimeException(new IOException("Disque plein"));

        assertThat(enveloppeOrdinaire.getMessage()).isEqualTo("java.io.IOException: Disque plein");
        assertThat(CauseLisible.messageDe(enveloppeOrdinaire)).isEqualTo("Disque plein");
    }

    @Test
    @DisplayName("Une chaîne entièrement muette dit le type et où regarder, jamais « null »")
    void une_chaine_muette_dit_le_type_et_ou_regarder() {
        // Le cas que l'alerte traitait le plus mal : `String.valueOf(null)` affichait « null » à un
        // naturaliste. Un type et une direction valent mieux qu'un mot qui ne veut rien dire.
        RuntimeException muette = new RuntimeException(new IllegalStateException());

        String message = CauseLisible.messageDe(muette);

        assertThat(message).contains("IllegalStateException").contains("journal");
        assertThat(message).doesNotContain("null");
        // Le geste conseillé doit être celui qui existe dans le menu, pas une paraphrase. La
        // première rédaction disait « ☰ → Journaux » ; l'entrée s'appelle « Ouvrir le dossier des
        // journaux ». Un message qui envoie chercher une ligne inexistante est le défaut que
        // l'ADR 3854 ferme, et ce test l'empêche de revenir par recopie.
        assertThat(message).contains(CauseLisible.LIBELLE_ENTREE_JOURNAUX);
    }

    @Test
    @DisplayName("Un message vide ou blanc ne compte pas comme un message")
    void un_message_blanc_ne_compte_pas() {
        assertThat(CauseLisible.messageDe(new RuntimeException("   ", new IOException("La carte a été retirée"))))
                .isEqualTo("La carte a été retirée");
    }

    @Test
    @DisplayName("Une chaîne de causes qui se referme sur elle-même ne fait pas boucler")
    void une_chaine_cyclique_ne_fait_pas_boucler() {
        // Rare, mais légal : `initCause` permet de refermer une chaîne. Un déroulement naïf y tourne
        // sans fin, et le filet global est exactement l'endroit où l'on ne peut pas se le permettre -
        // c'est déjà lui qui a bouclé seize mille fois en #3700.
        RuntimeException premiere = new RuntimeException();
        RuntimeException seconde = new RuntimeException(premiere);
        premiere.initCause(seconde);

        assertThat(CauseLisible.messageDe(premiere)).contains("RuntimeException");
    }

    @Test
    @DisplayName("Sans exception du tout, le filet dit quelque chose plutôt que de lever")
    void sans_exception_le_filet_ne_leve_pas() {
        // Un signalement qui échoue en composant son propre message ne signale rien : c'est la forme
        // même du défaut de #3700.
        assertThat(CauseLisible.messageDe(null)).isNotBlank();
    }
}
