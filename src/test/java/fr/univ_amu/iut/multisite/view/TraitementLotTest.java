package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.SuiviOperation;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.TexteCompteRendu;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.LignePassage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.stage.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests du geste **traiter la sélection** (#2357), joué de bout en bout avec les trois ports doublés.
///
/// C'est précisément ce que le choix de conception rend possible : ne rien inventer comme fenêtre, et
/// n'employer que des ports injectables, permet d'éprouver le geste **jusqu'à son effet** sans écran.
/// Une modale dédiée aurait exigé TestFX pour vérifier la même chose.
///
/// Le sujet du lot 3 étant le chemin non nominal, on couvre : le renoncement, la sélection entièrement
/// écartée, l'échec d'un passage qui n'arrête pas les autres, et l'annulation.
class TraitementLotTest {

    /// Confirmateur doublé : capture ce qui a été annoncé, et répond ce qu'on lui dit de répondre.
    ///
    /// Il retient aussi le compte rendu **structuré**. Le repli textuel du port aplatit la structure, et
    /// la sévérité disparaît dans l'aplatissement : une assertion sur le texte ne peut pas voir qu'une
    /// annonce est passée d'AVERTISSEMENT à INFO. La mesure de mutation l'a dit - la conditionnelle qui
    /// choisit la sévérité survivait.
    private static final class ConfirmateurDouble implements fr.univ_amu.iut.commun.view.Confirmateur {
        private final boolean reponse;
        private String question = "";
        private CompteRendu structure;

        ConfirmateurDouble(boolean reponse) {
            this.reponse = reponse;
        }

        @Override
        public boolean confirmer(String message) {
            question = message;
            return reponse;
        }

        @Override
        public boolean confirmer(CompteRendu compteRendu) {
            structure = compteRendu;
            return confirmer(TexteCompteRendu.rendre(compteRendu));
        }
    }

    /// Notificateur doublé : capture le compte rendu final.
    private static final class NotificateurDouble implements fr.univ_amu.iut.commun.view.Notificateur {
        private NiveauNotification niveau;
        private String message = "";

        @Override
        public void notifier(NiveauNotification niveau, String entete, String message) {
            this.niveau = niveau;
            this.message = message;
        }
    }

    /// Suivi doublé : exécute le travail **tout de suite**, sur le fil courant, avec un jeton qu'on
    /// peut pré-annuler. Aucun écran, aucun fil d'arrière-plan : le test reste déterministe.
    private static final class SuiviDouble implements SuiviOperation {
        private final boolean annuleAvant;

        /// Panne du suivi lui-même (pas d'un passage) : le lot n'a pas pu être mené. `null` = chemin
        /// nominal.
        private Throwable panne;

        SuiviDouble(boolean annuleAvant) {
            this.annuleAvant = annuleAvant;
        }

        @Override
        public <T> void lancer(
                Window proprietaire,
                String titre,
                BiFunction<Consumer<Progression>, JetonAnnulation, T> travail,
                Consumer<T> succes,
                Runnable annule,
                Consumer<Throwable> echec) {
            if (panne != null) {
                echec.accept(panne);
                return;
            }
            JetonAnnulation jeton = new JetonAnnulation();
            if (annuleAvant) {
                jeton.annuler();
            }
            succes.accept(travail.apply(progression -> {}, jeton));
        }
    }

    /// Action doublée : écarte les passages dont l'identifiant figure dans `aEcarter`, échoue sur ceux
    /// de `aEchouer`, réussit sur les autres, et note ce qu'elle a exécuté.
    private static final class ActionDouble implements ActionGroupee {
        private final List<Long> aEcarter;
        private final List<Long> aEchouer;
        private final List<Long> executes = new ArrayList<>();
        private Supplier<RuntimeException> leve = () -> new IllegalStateException("disque plein");

        ActionDouble(List<Long> aEcarter, List<Long> aEchouer) {
            this.aEcarter = aEcarter;
            this.aEchouer = aEchouer;
        }

        @Override
        public String libelle() {
            return "Préparer le dépôt";
        }

        @Override
        public Optional<String> motifNonEligible(CiblePassage cible) {
            return aEcarter.contains(cible.idPassage()) ? Optional.of("déjà déposé") : Optional.empty();
        }

        @Override
        public void executer(CiblePassage cible, JetonAnnulation jeton) {
            executes.add(cible.idPassage());
            if (aEchouer.contains(cible.idPassage())) {
                throw leve.get();
            }
        }
    }

    private static LignePassage ligne(long id, String point) {
        return new LignePassage(
                id,
                "640380",
                point,
                2026,
                1,
                "2026-06-20",
                StatutWorkflow.VERIFIE,
                Verdict.OK,
                EtatAnalyse.SANS_OBJET,
                null,
                null);
    }

    @Test
    @DisplayName("l'annonce dit combien seront traités, et NOMME les écartés avec leur motif")
    void annonce_les_ecartes_avant_de_lancer() {
        ConfirmateurDouble confirmateur = new ConfirmateurDouble(false);
        NotificateurDouble notificateur = new NotificateurDouble();
        ActionDouble action = new ActionDouble(List.of(2L), List.of());

        new TraitementLot(confirmateur, notificateur, new SuiviDouble(false))
                .lancer(null, action, List.of(ligne(1, "A1"), ligne(2, "B2")), () -> {});

        assertThat(confirmateur.question).contains("1 passage(s) sur 2");
        assertThat(confirmateur.question)
                .as("nommer l'écarté : un décompte seul obligerait à deviner lequel")
                .contains("640380 / B2 / 2026 n°1")
                .contains("déjà déposé");
    }

    @Test
    @DisplayName("renoncer à l'annonce ne traite rien")
    void renoncer_ne_traite_rien() {
        ActionDouble action = new ActionDouble(List.of(), List.of());

        new TraitementLot(new ConfirmateurDouble(false), new NotificateurDouble(), new SuiviDouble(false))
                .lancer(null, action, List.of(ligne(1, "A1")), () -> {});

        assertThat(action.executes).isEmpty();
    }

    @Test
    @DisplayName("une sélection entièrement écartée n'ouvre aucun suivi et ne rend aucun compte")
    void selection_entierement_ecartee() {
        NotificateurDouble notificateur = new NotificateurDouble();
        ActionDouble action = new ActionDouble(List.of(1L, 2L), List.of());

        new TraitementLot(new ConfirmateurDouble(true), notificateur, new SuiviDouble(false))
                .lancer(null, action, List.of(ligne(1, "A1"), ligne(2, "B2")), () -> {});

        assertThat(action.executes).isEmpty();
        assertThat(notificateur.message)
                .as("l'annonce a déjà tout dit : un compte rendu vide serait du bruit")
                .isEmpty();
    }

    @Test
    @DisplayName("un passage en échec n'arrête pas les autres, et le compte rendu dit lequel")
    void un_echec_n_arrete_pas_le_lot() {
        NotificateurDouble notificateur = new NotificateurDouble();
        ActionDouble action = new ActionDouble(List.of(), List.of(1L));
        List<String> rafraichi = new ArrayList<>();

        new TraitementLot(new ConfirmateurDouble(true), notificateur, new SuiviDouble(false))
                .lancer(null, action, List.of(ligne(1, "A1"), ligne(2, "B2")), () -> rafraichi.add("oui"));

        assertThat(action.executes)
                .as("le second passage est traité malgré l'échec du premier")
                .containsExactly(1L, 2L);
        assertThat(notificateur.message).contains("disque plein").contains("640380 / A1");
        assertThat(notificateur.niveau).isEqualTo(NiveauNotification.AVERTISSEMENT);
        assertThat(rafraichi)
                .as("le tableau se rafraîchit : les statuts ont bougé")
                .hasSize(1);
    }

    @Test
    @DisplayName("un lot entièrement réussi rend un compte de niveau information")
    void lot_impeccable() {
        NotificateurDouble notificateur = new NotificateurDouble();

        new TraitementLot(new ConfirmateurDouble(true), notificateur, new SuiviDouble(false))
                .lancer(null, new ActionDouble(List.of(), List.of()), List.of(ligne(1, "A1")), () -> {});

        assertThat(notificateur.niveau).isEqualTo(NiveauNotification.INFORMATION);
        assertThat(notificateur.message).contains("1 réussi(s)");
    }

    @Test
    @DisplayName("ADR 2635 : un refus d'environnement arrive dans le compte rendu AVEC son geste")
    void un_refus_d_environnement_porte_son_geste() {
        NotificateurDouble notificateur = new NotificateurDouble();
        ActionDouble action = new ActionDouble(List.of(), List.of(1L, 2L));
        // Le jeton expire en cours de lot : les passages restants échouent tous sur le même besoin.
        action.leve = () -> new RegleMetierException(
                "Les observations de cette nuit n'ont pas pu être lues : l'application n'est pas"
                        + " connectée à Vigie-Chiro.",
                new Besoin.Connexion());

        new TraitementLot(new ConfirmateurDouble(true), notificateur, new SuiviDouble(false))
                .lancer(null, action, List.of(ligne(1, "A1"), ligne(2, "B2")), () -> {});

        assertThat(notificateur.message)
                .as("le fait vient du modèle, le geste de l'application : sans lui, vingt refus sans remède")
                .contains("n'est pas connectée à Vigie-Chiro.")
                .contains("menu ☰ > Se connecter à Vigie-Chiro");
    }

    @Test
    @DisplayName("le compte rendu compte les NON TRAITÉS, pas seulement les réussis")
    void compte_rendu_ventile_les_deux_nombres() {
        // Trouvé par mutation : remplacer la soustraction par une addition survivait. Les tests ne
        // lisaient que « N réussi(s) », jamais le second nombre - un lot de trois nuits dont une réussit
        // pouvait annoncer « 1 réussi(s), 4 non traité(s) » sans que rien ne bronche.
        NotificateurDouble notificateur = new NotificateurDouble();
        ActionDouble action = new ActionDouble(List.of(2L), List.of(3L));

        new TraitementLot(new ConfirmateurDouble(true), notificateur, new SuiviDouble(false))
                .lancer(null, action, List.of(ligne(1, "A1"), ligne(2, "B2"), ligne(3, "C3")), () -> {});

        assertThat(notificateur.message)
                .as("des deux passages LANCÉS, un a réussi et un a échoué")
                .contains("1 réussi(s), 1 non traité(s)");
        assertThat(notificateur.message)
                .as("l'écarté n'a jamais été lancé : il a été annoncé avant, il ne compte pas ici")
                .doesNotContain("640380 / B2");
    }

    @Test
    @DisplayName("l'annonce porte sa SÉVÉRITÉ : avertir quand rien ne sera fait, informer sinon")
    void annonce_porte_sa_severite() {
        // La sévérité disparaît dans la mise à plat textuelle : elle se lit sur la structure.
        ConfirmateurDouble tout = new ConfirmateurDouble(false);
        new TraitementLot(tout, new NotificateurDouble(), new SuiviDouble(false))
                .lancer(null, new ActionDouble(List.of(), List.of()), List.of(ligne(1, "A1")), () -> {});

        ConfirmateurDouble rien = new ConfirmateurDouble(false);
        new TraitementLot(rien, new NotificateurDouble(), new SuiviDouble(false))
                .lancer(null, new ActionDouble(List.of(1L), List.of()), List.of(ligne(1, "A1")), () -> {});

        assertThat(tout.structure.constats().getFirst().severite()).isEqualTo(Severite.INFO);
        assertThat(rien.structure.constats().getFirst().severite())
                .as("rien ne sera fait : l'annonce doit alerter, pas informer")
                .isEqualTo(Severite.AVERTISSEMENT);
        assertThat(rien.structure.constats())
                .as("le bloc des écartés s'ajoute quand il y en a, et lui seul les nomme")
                .hasSize(2);
        assertThat(tout.structure.constats())
                .as("rien d'écarté : pas de bloc vide")
                .hasSize(1);
        // La question posée n'est pas la même selon qu'il reste quelque chose à faire : demander
        // « Lancer le traitement ? » quand rien ne sera lancé ferait attendre un effet qui ne viendra pas.
        assertThat(tout.structure.conclusion()).isEqualTo("Lancer le traitement ?");
        assertThat(rien.structure.conclusion()).isEqualTo("Rien ne sera fait.");
    }

    @Test
    @DisplayName("le lot qui ne peut pas être MENÉ le dit, au lieu de se taire")
    void panne_du_suivi_est_dite() {
        // Chemin sans aucune couverture avant la mesure de mutation : le rappel d'échec du suivi n'était
        // jamais joué. Ce n'est pas l'échec d'un passage, c'est celui du lot entier.
        NotificateurDouble notificateur = new NotificateurDouble();
        SuiviDouble suivi = new SuiviDouble(false);
        suivi.panne = new IllegalStateException("le fil de tâches est mort");
        ActionDouble action = new ActionDouble(List.of(), List.of());

        new TraitementLot(new ConfirmateurDouble(true), notificateur, suivi)
                .lancer(null, action, List.of(ligne(1, "A1")), () -> {});

        assertThat(action.executes).isEmpty();
        assertThat(notificateur.message).contains("Le lot n'a pas pu être mené").contains("fil de tâches est mort");
        assertThat(notificateur.niveau).isEqualTo(NiveauNotification.AVERTISSEMENT);
    }

    @Test
    @DisplayName("annulé avant le premier passage : rien n'est touché, et le compte rendu le dit")
    void annulation_ne_touche_rien() {
        NotificateurDouble notificateur = new NotificateurDouble();
        ActionDouble action = new ActionDouble(List.of(), List.of());

        new TraitementLot(new ConfirmateurDouble(true), notificateur, new SuiviDouble(true))
                .lancer(null, action, List.of(ligne(1, "A1"), ligne(2, "B2")), () -> {});

        assertThat(action.executes)
                .as("le jeton est consulté AVANT chaque passage : aucun n'est commencé")
                .isEmpty();
        assertThat(notificateur.message).contains("lot interrompu").contains("non traité");
    }
}
