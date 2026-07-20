package fr.univ_amu.iut.maj.model;

import java.util.Optional;

/// Port **sortant** : où l'on va lire la dernière version publiée (#2109).
///
/// Il existe pour que le service de décision ([VerificateurMiseAJour]) soit testable **sans réseau**.
/// Sans lui, vérifier « une version plus récente déclenche l'annonce » exigerait un vrai appel HTTP :
/// le test serait lent, dépendant d'internet, et surtout incapable d'exercer les cas qui comptent -
/// la machine hors ligne, l'amont injoignable, une réponse illisible.
///
/// **Best-effort par contrat** : toute défaillance rend [Optional#empty()] plutôt que de lever. Une
/// vérification de mise à jour est un confort ; elle ne doit jamais empêcher l'application de
/// démarrer, ni faire surgir une erreur à quelqu'un qui n'a rien demandé.
@FunctionalInterface
public interface DerniereVersionPubliee {

    /// La dernière version publiée, ou vide si elle n'a pas pu être obtenue - quelle qu'en soit la
    /// raison, et sans distinguer : l'appelant n'a rien à en faire de différent.
    Optional<VersionDisponible> consulter();
}
