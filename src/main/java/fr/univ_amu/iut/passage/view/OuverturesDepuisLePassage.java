package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.OuvrirActivite;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import java.util.Optional;

/// **Ce qu'on peut ouvrir depuis un passage** : les sept écrans que sa fiche sait joindre.
///
/// ## Pourquoi un groupe, et pourquoi celui-ci
///
/// `PassageController` recevait ces sept ports un par un, soit sept de ses douze paramètres. Le
/// fichier de règles PMD du dépôt dit ce qu'il attend passé onze : « **11 reste un garde-fou :
/// au-delà, un Parameter Object est attendu.** » Le lot #5310 a fait franchir cette limite en
/// ajoutant un collaborateur, et le seuil ne se relève pas.
///
/// Le groupe n'est donc pas une commodité de signature. Il nomme quelque chose que le code disait
/// déjà sans le dire : cette fiche est un **carrefour**, et sept écrans partent d'elle.
///
/// ## Ce qui reste `Optional`, et pourquoi
///
/// Cinq de ces écrans appartiennent à des **features désactivables**. Un contrat non lié rend un
/// `Optional` vide, et la carte correspondante se masque - c'est ainsi que le produit se compose. Les
/// grouper ne change pas cela : chaque port garde l'optionalité qu'il avait.
public record OuverturesDepuisLePassage(
        Optional<OuvrirVerification> verification,
        Optional<OuvrirDiagnostic> diagnostic,
        Optional<OuvrirActivite> activite,
        OuvrirValidation validation,
        Optional<OuvrirLot> lot,
        OuvrirSite site,
        OuvrirMultisite multisite) {

    @Inject
    public OuverturesDepuisLePassage {}
}
