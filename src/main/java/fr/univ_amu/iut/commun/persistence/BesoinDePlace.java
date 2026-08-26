package fr.univ_amu.iut.commun.persistence;

import fr.univ_amu.iut.commun.model.EspaceDisque;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/// Ce qu'une restauration complète va demander au disque, **lu dans le manifeste** (#3563).
///
/// Aucun parcours de fichiers ici : chaque [RacineSauvegardee] porte déjà ses `octets`. Le choix du
/// régime se fait donc avant d'avoir touché quoi que ce soit, ce qui est toute l'idée - un refus
/// tardif laisse une destination à moitié écrite.
///
/// ## Pourquoi par dossier d'accueil, et non un total unique
///
/// Les nuits ne reviennent pas toutes au même endroit : celle dont le disque externe est rebranché
/// retourne dessus, les autres atterrissent dans le dossier de travail. Un total unique confronté à
/// la place du seul dossier de travail se tromperait **dans le sens dangereux** - il annoncerait que
/// tout tient alors que le disque externe est plein, et la copie échouerait à mi-parcours.
///
/// @param parDossierDAccueil pour chaque dossier où des nuits vont atterrir, ce qu'elles y pèsent
record BesoinDePlace(Map<Path, Besoin> parDossierDAccueil) {

    /// Ce que pèsent, à un endroit donné, les nuits qui y vont : leur somme, et la plus lourde.
    ///
    /// @param total ce qu'il faut pour les étaler **toutes** avant d'en basculer une seule
    /// @param plusGrosse ce qu'il faut pour en étaler **une** à la fois
    record Besoin(long total, long plusGrosse) {

        Besoin plus(Besoin autre) {
            return new Besoin(total + autre.total, Math.max(plusGrosse, autre.plusGrosse));
        }
    }

    /// @param destinationPour où chaque racine atterrira, d'après son chemin d'origine
    static BesoinDePlace de(ManifesteSauvegarde manifeste, UnaryOperator<Path> destinationPour) {
        Map<Path, Besoin> parDossier = new LinkedHashMap<>();
        for (RacineSauvegardee racine : manifeste.racines()) {
            Path accueil =
                    destinationPour.apply(Path.of(racine.cheminOrigine())).getParent();
            if (accueil != null) {
                parDossier.merge(accueil, new Besoin(racine.octets(), racine.octets()), Besoin::plus);
            }
        }
        return new BesoinDePlace(Map.copyOf(parDossier));
    }

    /// Le régime que la place libre autorise, ou un refus **chiffré** si même une nuit ne tient pas.
    ///
    /// ## Pourquoi une échelle, et pas un verrou
    ///
    /// L'ADR 2727 avait écarté la zone temporaire parce qu'elle « ferait échouer la restauration la
    /// plus courante, celle où l'on remet ses propres données, pour protéger d'un cas plus rare ». Le
    /// reproche portait, et il porte encore : refuser ce qui aurait été possible rend rigide un
    /// dispositif censé protéger.
    ///
    /// La réponse n'est donc ni la zone temporaire partout, ni nulle part : c'est **dégrader la
    /// garantie plutôt que l'usage**. Quand la place manque pour tout étaler, on étale une nuit à la
    /// fois - chacune reste tout-ou-rien, l'ensemble ne l'est plus - et **on le dit**.
    ///
    /// Le refus du dernier rang n'est pas une rigidité de plus : en dessous de la plus grosse nuit,
    /// aucun régime ne tient. Le seul plus permissif serait la copie en place, qui rendrait à
    /// l'utilisateur la destination à moitié écrite dont ce chemin vient de le débarrasser.
    ///
    /// @throws RefusAvantEcriture s'il manque de quoi étaler ne serait-ce qu'une nuit, ou si la place
    ///     ne peut pas être connue - le port dit qu'un appelant sur le point d'écrire refuse plutôt
    ///     que de tenter
    RegimeRestauration regimePour(EspaceDisque espace) {
        boolean toutTient = true;
        for (Map.Entry<Path, Besoin> accueil : parDossierDAccueil.entrySet()) {
            long libre = libre(espace, accueil.getKey());
            Besoin besoin = accueil.getValue();
            if (libre < besoin.plusGrosse()) {
                throw refusChiffre(accueil.getKey(), besoin.plusGrosse(), libre);
            }
            toutTient = toutTient && libre >= besoin.total();
        }
        return toutTient ? RegimeRestauration.ENSEMBLE : RegimeRestauration.RACINE_PAR_RACINE;
    }

    private static long libre(EspaceDisque espace, Path dossier) {
        try {
            return espace.disponibleOctets(dossier);
        } catch (IOException illisible) {
            throw new RefusAvantEcriture(
                    "Impossible de connaître la place disponible dans " + dossier
                            + " : la restauration est annulée plutôt que tentée à l'aveugle."
                            + " Rien n'a été touché.",
                    illisible);
        }
    }

    private static RefusAvantEcriture refusChiffre(Path accueil, long plusGrosse, long libre) {
        // `octetsLisibles` tronque au kilo-octet : un manque de quelques centaines d'octets s'afficherait
        // « Libérez 0 Ko », soit un refus qui demande de ne rien libérer. On ne descend pas sous 1 Ko.
        long aLiberer = Math.max(1024, plusGrosse - libre);
        return new RefusAvantEcriture(
                "Il n'y a pas assez de place dans " + accueil + " pour restaurer sans risque : la plus"
                        + " grosse nuit pèse " + Formats.octetsLisibles(plusGrosse) + " et il reste "
                        + Formats.octetsLisibles(libre) + ". Libérez "
                        + Formats.octetsLisibles(aLiberer)
                        + ", ou restaurez vers un autre emplacement. Rien n'a été touché.",
                null);
    }
}
