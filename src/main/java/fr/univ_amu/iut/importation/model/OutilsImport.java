package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.EspaceDisque;

/// Les **outils** dont l'import a besoin pour écrire : copier, renommer, transformer, et savoir s'il
/// reste de la place.
///
/// Ils n'ont d'existence que pour construire les collaborateurs internes de [ServiceImport]
/// ([PreparationOriginaux], [DecoupageParallele], [MoteurImport]) : les regrouper évite d'étaler quatre
/// dépendances de plus sur une signature qui en portait déjà onze, et dit ce qu'elles ont en commun —
/// ce sont les **moyens d'écriture** de l'import, par opposition aux DAO, à l'horloge ou au workspace.
///
/// C'est aussi ce qui rend le garde-fou d'espace disque **testable** (#2041) : un test injecte un
/// disque presque plein sans dépendre de la machine, là où une lecture réelle codée en dur rendrait le
/// refus invérifiable.
///
/// @param copie copie protégée SD → workspace (R9 : ne jamais écrire sur la source)
/// @param renommeur application du préfixe R6 aux originaux
/// @param transformation découpe et expansion des WAV en séquences d'écoute
/// @param espaceDisque lecture de l'espace disponible, pour refuser avant d'écrire
public record OutilsImport(
        CopieProtegee copie, Renommeur renommeur, TransformationAudio transformation, EspaceDisque espaceDisque) {

    /// Les outils réels, avec la lecture disque du système de fichiers.
    public static OutilsImport reels(CopieProtegee copie, Renommeur renommeur, TransformationAudio transformation) {
        return new OutilsImport(copie, renommeur, transformation, EspaceDisque.reel());
    }
}
