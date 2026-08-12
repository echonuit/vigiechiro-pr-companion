package fr.univ_amu.iut.maj.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.AnnonceChrome;
import fr.univ_amu.iut.maj.model.ConseilDeMiseAJour;
import fr.univ_amu.iut.maj.model.VerificateurMiseAJour;
import fr.univ_amu.iut.maj.model.VersionDisponible;
import java.util.Objects;
import java.util.Optional;

/// Annonce du chrome : « une version plus récente existe » (#2109).
///
/// Contribution de la feature `maj` au point d'extension [AnnonceChrome] du socle. Toute la décision
/// vit dans [VerificateurMiseAJour] ; cette classe ne fait que l'habiller, et ne décide de rien -
/// notamment pas du silence, qui lui arrive déjà sous la forme d'un `Optional` vide.
///
/// Le message nomme les **deux** versions plutôt que la seule nouvelle. « Une mise à jour est
/// disponible » ne dit pas à l'utilisateur s'il a deux jours ou deux ans de retard, alors que la
/// réponse change ce qu'il en fait.
public final class AnnonceMiseAJour implements AnnonceChrome {

    private final VerificateurMiseAJour verificateur;
    private final fr.univ_amu.iut.commun.model.VersionApplication version;

    @Inject
    AnnonceMiseAJour(VerificateurMiseAJour verificateur, fr.univ_amu.iut.commun.model.VersionApplication version) {
        this.verificateur = Objects.requireNonNull(verificateur, "verificateur");
        this.version = Objects.requireNonNull(version, "version");
    }

    @Override
    public Optional<Annonce> chercher() {
        return verificateur
                .versionAProposer()
                .map(disponible -> new Annonce(message(disponible), "Voir cette version", disponible.adresse()));
    }

    /// Le message, augmenté du geste conseillé quand il y en a un.
    ///
    /// La phrase de conseil vient du modèle et non d'ici : `verifier-maj` rend **exactement la même**
    /// (ADR 0014). La composer dans la vue la ferait diverger de la CLI au premier ajustement.
    private String message(VersionDisponible disponible) {
        String base =
                "La version " + disponible.numero() + " est disponible (vous utilisez la " + version.libelle() + ").";
        return ConseilDeMiseAJour.pourCeSysteme()
                .map(conseil -> base + " " + conseil)
                .orElse(base);
    }
}
