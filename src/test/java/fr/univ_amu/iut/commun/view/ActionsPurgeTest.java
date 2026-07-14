package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.persistence.DeclarationPurgeOriginaux;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux.ResultatPurge;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le **geste** de purge des originaux, joué pour de vrai (#1405).
///
/// C'est l'action la plus destructive de l'application : elle supprime les enregistrements bruts de
/// **toutes** les nuits importées, et VigieChiro ne les rend pas. Elle n'était pourtant couverte nulle
/// part - la cause est mécanique et vaut pour tous les écrans : l'action se terminait par un
/// `Alert.showAndWait()` en dur, qui **fige** un test headless. On ne pouvait donc pas la déclencher,
/// seulement constater que son entrée de menu existait.
///
/// Le compte rendu passe maintenant par le port [Notificateur], comme le oui/non passait déjà par
/// [Confirmateur]. Les deux dialogues deviennent des doubles, et le geste se vérifie **jusqu'à son
/// effet** : ce qui a été supprimé, ce qui a été déclaré (#1303), et ce qui a été dit.
///
/// L'exécution est synchrone de bout en bout ([ExecuteurTacheSynchrone]) : le voile du chrome n'est
/// pas installé ici (contexte partiel), ce qui n'ôte aucune garantie ([OccupationChromeTest]).
class ActionsPurgeTest {

    private static final long RECUPERABLE = 4_294_967_296L;

    private final ServicePurgeOriginaux service = mock(ServicePurgeOriginaux.class);
    private final DeclarationPurgeOriginaux declaration = mock(DeclarationPurgeOriginaux.class);
    private final OccupationChrome occupation =
            new OccupationChrome(new ExecuteurTacheSynchrone(), new NavigationViewModel());

    /// Ce que le confirmateur a **demandé** : le message est un contenu à part entière (il annonce le
    /// gain, ce qui est conservé, et que la suppression est définitive).
    private final List<String> confirmations = new ArrayList<>();

    /// Ce que le notificateur a **dit**, au lieu de l'afficher.
    private final List<String> annonces = new ArrayList<>();

    private final List<NiveauNotification> niveaux = new ArrayList<>();

    /// Nombre de fois où l'appelant a été rafraîchi (retour à l'accueil : les volumes affichés ont changé).
    private int rafraichissements;

    /// Ce que le double de confirmation répondra : chaque test le pose avant de déclencher.
    private boolean confirme = true;

    /// Action sous test, avec ses deux dialogues remplacés par des doubles.
    private ActionsPurge action(Optional<DeclarationPurgeOriginaux> declarationPurge) {
        ActionsPurge action =
                new ActionsPurge(service, occupation, () -> null, () -> rafraichissements++, declarationPurge);
        action.confirmateur().definir(message -> {
            confirmations.add(message);
            return confirme;
        });
        action.notificateur().definir((niveau, entete, message) -> {
            niveaux.add(niveau);
            annonces.add(entete + " | " + message);
        });
        return action;
    }

    private ActionsPurge action() {
        return action(Optional.of(declaration));
    }

    @Test
    @DisplayName(
            "#1405 : purge confirmée : les bruts sont supprimés, le geste est déclaré, l'espace libéré est annoncé")
    void purge_confirmee_supprime_declare_et_rend_compte() {
        when(service.volumeRecuperable()).thenReturn(RECUPERABLE);
        when(service.purgerTout()).thenReturn(new ResultatPurge(3, RECUPERABLE));

        action().purger();

        // La confirmation dit ce qu'on gagne, ce qu'on garde, et que la suppression ne se rattrape pas.
        assertThat(confirmations)
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .contains("pour libérer environ")
                        .contains("Les séquences d'écoute")
                        .as("le consentement porte sur une suppression sans retour")
                        .contains("définitive"));
        verify(service).purgerTout();
        // Sans cette déclaration (#1303), l'audit prendrait les bruts purgés pour une corruption.
        verify(declaration).declarerPurgeGlobale();
        assertThat(rafraichissements)
                .as("les volumes affichés ont changé : l'appelant est rafraîchi")
                .isEqualTo(1);
        assertThat(niveaux).containsExactly(NiveauNotification.INFORMATION);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce -> assertThat(annonce)
                        .contains("Originaux purgés")
                        .contains("3 nuit(s) purgée(s)")
                        .contains("libéré"));
    }

    @Test
    @DisplayName("#1405 : confirmation refusée : rien n'est supprimé, rien n'est déclaré, rien n'est annoncé")
    void refus_de_confirmation_ne_supprime_rien() {
        when(service.volumeRecuperable()).thenReturn(RECUPERABLE);
        confirme = false;

        action().purger();

        verify(service, never()).purgerTout();
        verify(declaration, never()).declarerPurgeGlobale();
        assertThat(rafraichissements).isZero();
        assertThat(annonces).as("un refus n'a pas à être commenté").isEmpty();
    }

    @Test
    @DisplayName("#1405 : rien à purger : on le dit, et on ne demande pas de confirmer une suppression vide")
    void rien_a_purger_informe_sans_confirmer() {
        when(service.volumeRecuperable()).thenReturn(0L);

        action().purger();

        assertThat(confirmations)
                .as("confirmer une suppression qui n'a pas lieu d'être serait une question pour rien")
                .isEmpty();
        verify(service, never()).purgerTout();
        assertThat(niveaux).containsExactly(NiveauNotification.INFORMATION);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce -> assertThat(annonce).contains("Rien à purger"));
    }

    @Test
    @DisplayName("#1405 : disque illisible à l'inspection : l'utilisateur est averti, on ne supprime rien")
    void echec_de_l_inspection_avertit_et_ne_supprime_rien() {
        when(service.volumeRecuperable()).thenThrow(new IllegalStateException("disque illisible"));

        action().purger();

        verify(service, never()).purgerTout();
        assertThat(confirmations).isEmpty();
        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce ->
                        assertThat(annonce).contains("Purge impossible").contains("disque illisible"));
    }

    @Test
    @DisplayName("#1405 : purge interrompue : l'utilisateur est averti, l'appelant n'est pas rafraîchi")
    void echec_de_la_purge_avertit() {
        when(service.volumeRecuperable()).thenReturn(RECUPERABLE);
        when(service.purgerTout()).thenThrow(new IllegalStateException("fichier verrouillé"));

        action().purger();

        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce ->
                        assertThat(annonce).contains("Purge impossible").contains("fichier verrouillé"));
        assertThat(rafraichissements)
                .as("rien n'a été libéré : rien à rafraîchir")
                .isZero();
    }

    @Test
    @DisplayName("#1405 : injecteur partiel (sans port de déclaration) : la purge a lieu quand même")
    void sans_port_de_declaration_la_purge_a_lieu() {
        when(service.volumeRecuperable()).thenReturn(RECUPERABLE);
        when(service.purgerTout()).thenReturn(new ResultatPurge(1, RECUPERABLE));

        action(Optional.empty()).purger();

        verify(service).purgerTout();
        assertThat(niveaux).containsExactly(NiveauNotification.INFORMATION);
        assertThat(rafraichissements).isEqualTo(1);
    }
}
