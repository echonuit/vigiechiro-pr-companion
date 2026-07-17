package fr.univ_amu.iut.connexion.viewmodel;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// ViewModel de la connexion VigieChiro (#727) : reflète l'état de connexion (identité en cache) et
/// pilote la connexion (vérifier un token collé via `GET /moi`) et la déconnexion.
///
/// Agnostique de l'IHM (`javafx.beans` uniquement). L'appel réseau de [#connecter] est **bloquant** :
/// le controller le lance hors du fil JavaFX, puis rafraîchit l'état affiché via [#rafraichir].
public class ConnexionViewModel {

    private final StockageConnexion stockage;
    private final ClientVigieChiro client;
    private final Set<RapprochementVigieChiro> rapprocheurs;

    /// Résumé humain de la dernière synchronisation (ex. « 385 taxons, 3 sites »), vide si aucune.
    /// Écrit hors du fil JavaFX par [#amorcerRapprochements], lu ensuite par le controller ; `volatile`
    /// pour la visibilité inter-threads.
    private volatile String resumeSynchro = "";

    private final ReadOnlyStringWrapper identite = new ReadOnlyStringWrapper(this, "identite", "");
    private final ReadOnlyBooleanWrapper connecte = new ReadOnlyBooleanWrapper(this, "connecte", false);
    private final ReadOnlyBooleanWrapper jetonEnregistre = new ReadOnlyBooleanWrapper(this, "jetonEnregistre", false);

    public ConnexionViewModel(
            StockageConnexion stockage, ClientVigieChiro client, Set<RapprochementVigieChiro> rapprocheurs) {
        this.stockage = Objects.requireNonNull(stockage, "stockage");
        this.client = Objects.requireNonNull(client, "client");
        this.rapprocheurs = Set.copyOf(Objects.requireNonNull(rapprocheurs, "rapprocheurs"));
    }

    /// Recalcule l'état affiché depuis le stockage local (sans réseau). À appeler sur le fil JavaFX.
    /// Trois états depuis #1369 : connecté (profil vérifié), **jeton enregistré non vérifié**
    /// (VigieChiro était injoignable au moment de la vérification : le jeton n'a pas été jeté), et
    /// non connecté.
    public void rafraichir() {
        Optional<ProfilVigieChiro> profil = stockage.profil();
        connecte.set(profil.isPresent());
        jetonEnregistre.set(stockage.estConnecte());
        identite.set(profil.map(ConnexionViewModel::libelle)
                .orElseGet(() -> stockage.estConnecte()
                        ? "Jeton enregistré, non vérifié (VigieChiro était injoignable)"
                        : "Non connecté"));
    }

    /// **Vérifie et enregistre** un token (opération réseau : `GET /moi`). Renvoie l'issue **triée**
    /// (#1284) : `Succes` (identité persistée), `Refuse` (le serveur a dit non : jeton invalide ou
    /// expiré → connexion effacée) ou `Injoignable` — et dans ce dernier cas, depuis #1369, le jeton
    /// est **conservé, non vérifié** : le réseau était peut-être seul en cause, et le jeton stocké
    /// permet à toutes les actions VigieChiro de refonctionner dès que la plateforme répond. Il sera
    /// revérifié à la prochaine ouverture de la modale ([#jetonAVerifier]). À lancer hors du fil
    /// JavaFX ; ne touche à aucune propriété (le controller appelle [#rafraichir] ensuite).
    public ReponseApi<ProfilVigieChiro> connecter(String token) {
        if (token == null || token.isBlank()) {
            return ReponseApi.nonConnecte();
        }
        String propre = token.trim();
        resumeSynchro = "";
        stockage.enregistrer(propre, null);
        ReponseApi<ProfilVigieChiro> profil = client.moi();
        switch (profil) {
            case ReponseApi.Succes<ProfilVigieChiro>(ProfilVigieChiro identiteVerifiee) -> {
                stockage.enregistrer(propre, identiteVerifiee);
                amorcerRapprochements();
            }
            case ReponseApi.Injoignable<ProfilVigieChiro> injoignable -> {
                // #1369 : impossible de vérifier n'est pas « jeton refusé » : on le garde, non vérifié.
            }
            case ReponseApi.NonConnecte<ProfilVigieChiro> nonConnecte -> stockage.effacer();
            case ReponseApi.Refuse<ProfilVigieChiro> refuse -> stockage.effacer();
        }
        return profil;
    }

    /// Jeton enregistré **jamais vérifié** (connexion tentée hors ligne, #1369), à revérifier à la
    /// prochaine occasion ; vide si non connecté ou si l'identité est déjà vérifiée.
    public Optional<String> jetonAVerifier() {
        return stockage.profil().isEmpty() ? stockage.token() : Optional.empty();
    }

    /// Amorce les correspondances locales ↔ VigieChiro (taxons, sites) juste après une connexion réussie
    /// (#728) et mémorise un résumé de ce qui a été synchronisé (#717). Chaque rapprocheur est
    /// **best-effort** (il avale ses propres erreurs) : un échec ne compromet pas la connexion. Déjà hors
    /// du fil JavaFX (appelé depuis [#connecter]).
    private void amorcerRapprochements() {
        List<String> parties = new ArrayList<>();
        // Ordre (#1776) : structure d'abord (sites, taxons), puis ce qui en dépend (passages sur points locaux).
        for (RapprochementVigieChiro rapprocheur : RapprochementVigieChiro.ordonnes(rapprocheurs)) {
            rapprocheur.synchroniser(client).ifPresent(rapport -> parties.add(rapport.enClair()));
        }
        resumeSynchro = String.join(", ", parties);
    }

    /// Résumé de la dernière synchronisation déclenchée par [#connecter] (ex. « 385 taxons, 3 sites »),
    /// ou chaîne vide si rien n'a été synchronisé. À lire après un `connecter` réussi.
    public String resumeSynchro() {
        return resumeSynchro;
    }

    /// Efface la connexion locale. À suivre d'un [#rafraichir].
    public void deconnecter() {
        stockage.effacer();
    }

    private static String libelle(ProfilVigieChiro profil) {
        String pseudo = profil.pseudo() == null ? "?" : profil.pseudo();
        String role = profil.role() == null ? "" : " (" + profil.role() + ")";
        return "Connecté : " + pseudo + role;
    }

    /// Libellé d'identité (« Connecté : X (Observateur) » ou « Non connecté »).
    public ReadOnlyStringProperty identiteProperty() {
        return identite.getReadOnlyProperty();
    }

    /// `true` si l'identité est **vérifiée** (profil présent) : pilote le verrouillage de la saisie.
    public ReadOnlyBooleanProperty connecteProperty() {
        return connecte.getReadOnlyProperty();
    }

    /// `true` si un jeton est **enregistré** (vérifié ou non, #1369) : pilote la déconnexion, qui doit
    /// aussi pouvoir effacer un jeton conservé hors ligne.
    public ReadOnlyBooleanProperty jetonEnregistreProperty() {
        return jetonEnregistre.getReadOnlyProperty();
    }
}
