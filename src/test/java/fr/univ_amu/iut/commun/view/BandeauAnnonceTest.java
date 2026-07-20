package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.AnnonceChrome.Annonce;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;

/// Le bandeau d'annonce se juge sur ce qu'il fait **quand il n'y a rien à dire** : c'est le cas le
/// plus fréquent, et celui où un défaut se voit le plus - une bande vide sous la barre de navigation,
/// à chaque lancement.
///
/// L'exécuteur est [ExecuteurTacheSynchrone] : la recherche s'y déroule sur place, ce qui rend le test
/// déterministe sans rien changer au code de production (c'est le défaut fixé par `@ImplementedBy`).
class BandeauAnnonceTest {

    @BeforeAll
    static void demarrerToolkit() throws Exception {
        FxToolkit.registerPrimaryStage();
    }

    private static final Annonce ANNONCE =
            new Annonce("La version 2.23.0 est disponible.", "Voir cette version", "https://exemple/v2.23.0");

    /// Capte les adresses ouvertes, pour vérifier le clic sans lancer de navigateur.
    private static final class OuvreurEspion implements OuvreurDeLien {
        private final List<String> ouvertes = new java.util.ArrayList<>();

        @Override
        public void ouvrir(String adresse) {
            ouvertes.add(adresse);
        }
    }

    private record Piece(HBox conteneur, Label texte, Hyperlink lien, Button fermer) {}

    private static Piece pieces() {
        return new Piece(new HBox(), new Label(), new Hyperlink(), new Button());
    }

    @Test
    @DisplayName("aucune annonce : le bandeau reste invisible ET non managé")
    void sansAnnonceLeBandeauNePrendAucunePlace() {
        Piece p = pieces();

        new BandeauAnnonce(Set.of(), new ExecuteurTacheSynchrone(), new OuvreurEspion())
                .installer(p.conteneur(), p.texte(), p.lien(), p.fermer());

        assertThat(p.conteneur().isVisible()).isFalse();
        assertThat(p.conteneur().isManaged())
                .as("non managé, sinon une bande vide s'intercale sous la barre de navigation")
                .isFalse();
    }

    @Test
    @DisplayName("une contribution qui n'a rien à dire ne montre rien")
    void contributionSilencieuseNeMontreRien() {
        Piece p = pieces();

        new BandeauAnnonce(Set.of(Optional::empty), new ExecuteurTacheSynchrone(), new OuvreurEspion())
                .installer(p.conteneur(), p.texte(), p.lien(), p.fermer());

        assertThat(p.conteneur().isVisible()).isFalse();
        assertThat(p.conteneur().isManaged()).isFalse();
    }

    @Test
    @DisplayName("une annonce s'affiche avec son message et son lien")
    void annonceSAfficheAvecSonLien() {
        Piece p = pieces();
        OuvreurEspion ouvreur = new OuvreurEspion();

        new BandeauAnnonce(Set.of(() -> Optional.of(ANNONCE)), new ExecuteurTacheSynchrone(), ouvreur)
                .installer(p.conteneur(), p.texte(), p.lien(), p.fermer());

        assertThat(p.conteneur().isVisible()).isTrue();
        assertThat(p.conteneur().isManaged()).isTrue();
        assertThat(p.texte().getText()).isEqualTo(ANNONCE.message());
        assertThat(p.lien().getText()).isEqualTo(ANNONCE.libelleAction());

        p.lien().fire();
        assertThat(ouvreur.ouvertes)
                .as("le lien doit mener où l'annonce le dit")
                .containsExactly(ANNONCE.adresseAction());
    }

    @Test
    @DisplayName("la croix referme le bandeau")
    void laCroixReferme() {
        Piece p = pieces();

        new BandeauAnnonce(Set.of(() -> Optional.of(ANNONCE)), new ExecuteurTacheSynchrone(), new OuvreurEspion())
                .installer(p.conteneur(), p.texte(), p.lien(), p.fermer());
        assertThat(p.conteneur().isVisible()).isTrue();

        p.fermer().fire();

        assertThat(p.conteneur().isVisible()).isFalse();
        assertThat(p.conteneur().isManaged()).isFalse();
    }

    @Test
    @DisplayName("sans contribution, aucune tâche de fond n'est lancée")
    void sansContributionAucuneRecherche() {
        // Feature désactivée : le Multibinder est vide. Lancer une tâche pour interroger un ensemble
        // vide serait un coût au démarrage sans aucune contrepartie.
        AtomicInteger taches = new AtomicInteger();
        // `ExecuteurTacheSynchrone` est final : on implémente le contrat en déléguant, plutôt que
        // d'en hériter.
        ExecuteurTache reel = new ExecuteurTacheSynchrone();
        ExecuteurTache compteur = new ExecuteurTache() {
            @Override
            public <T> void executer(
                    java.util.function.Supplier<T> travail,
                    java.util.function.Consumer<T> succes,
                    java.util.function.Consumer<Throwable> echec) {
                taches.incrementAndGet();
                reel.executer(travail, succes, echec);
            }

            @Override
            public java.util.concurrent.Executor surFilJavaFx() {
                return reel.surFilJavaFx();
            }
        };
        Piece p = pieces();

        new BandeauAnnonce(Set.of(), compteur, new OuvreurEspion())
                .installer(p.conteneur(), p.texte(), p.lien(), p.fermer());

        assertThat(taches).hasValue(0);
    }

    @Test
    @DisplayName("une contribution qui lève ne casse pas le démarrage")
    void contributionQuiLeveNeCassePas() {
        Piece p = pieces();

        // Le contrat demande de rendre vide plutôt que de lever ; ce test couvre l'implémentation qui
        // l'oublierait. Une annonce est un confort : elle ne doit jamais empêcher l'application de
        // s'ouvrir.
        new BandeauAnnonce(
                        Set.of(() -> {
                            throw new IllegalStateException("amont cassé");
                        }),
                        new ExecuteurTacheSynchrone(),
                        new OuvreurEspion())
                .installer(p.conteneur(), p.texte(), p.lien(), p.fermer());

        assertThat(p.conteneur().isVisible()).isFalse();
    }
}
