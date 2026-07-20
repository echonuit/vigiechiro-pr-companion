package fr.univ_amu.iut.maj.model;

import fr.univ_amu.iut.commun.model.VersionApplication;
import java.util.Objects;
import java.util.Optional;

/// Décide s'il y a lieu d'annoncer une mise à jour (#2109).
///
/// Toute la logique tient dans une règle : **on n'annonce que ce dont on est sûr**. Le silence est le
/// comportement par défaut, et chaque cas d'incertitude y retombe explicitement plutôt que d'être
/// deviné par l'appelant.
///
/// Quatre situations mènent au silence, et aucune n'est une anomalie :
///
/// - **l'application ne connaît pas sa version** (lancée hors d'un jar : `javafx:run`, tests, outils
///   de capture). Il n'y a alors aucune référence à comparer - annoncer reviendrait à comparer à une
///   valeur inventée ;
/// - **sa version n'est pas un numéro lisible** (`1.0-SNAPSHOT`, une pré-version) ;
/// - **l'amont est muet** : machine hors ligne, service injoignable, limite de débit atteinte,
///   réponse inattendue. Le port ne distingue pas ces cas, et l'utilisateur n'en ferait rien ;
/// - **la version locale est supérieure ou égale** à la dernière publiée. Le cas « supérieure » n'est
///   pas absurde : c'est celui d'un binaire construit depuis `main` avant publication.
public final class VerificateurMiseAJour {

    private final VersionApplication versionLocale;
    private final DerniereVersionPubliee amont;

    public VerificateurMiseAJour(VersionApplication versionLocale, DerniereVersionPubliee amont) {
        this.versionLocale = Objects.requireNonNull(versionLocale, "versionLocale");
        this.amont = Objects.requireNonNull(amont, "amont");
    }

    /// La version à proposer, ou vide s'il n'y a rien à dire.
    ///
    /// Un `Optional` vide couvre indistinctement « tout va bien, vous êtes à jour » et « je n'ai pas
    /// pu savoir ». C'est délibéré : l'appelant se tait dans les deux cas, et distinguer l'ignorance
    /// de la satisfaction n'apporterait à l'utilisateur qu'un message dont il ne peut rien faire.
    public Optional<VersionDisponible> versionAProposer() {
        Optional<NumeroDeVersion> locale = versionLocale.versionEmpaquetee().flatMap(NumeroDeVersion::lire);
        if (locale.isEmpty()) {
            return Optional.empty();
        }
        return amont.consulter().filter(publiee -> publiee.numero().compareTo(locale.orElseThrow()) > 0);
    }
}
