package fr.univ_amu.iut.commun.persistence;

/// Un **refus** de la couche persistance, émis **avant** d'avoir écrit quoi que ce soit (#3146).
///
/// La couche persistance levait jusqu'ici la même exception pour deux situations que rien ne permet
/// de confondre du point de vue de l'appelant :
///
/// - une **panne** en cours d'écriture, où l'état est incertain et où la pile est l'information
///   utile ([DataAccessException] tout court) ;
/// - un **refus** qui n'a touché à rien : le fichier désigné n'est pas une sauvegarde, elle vient
///   d'une version plus récente, le dossier de travail est occupé. L'utilisateur peut agir, et
///   l'état local est intact.
///
/// La distinction n'est pas cosmétique : la CLI en tire son **code de sortie**, et un script qui
/// enchaîne ne peut agir que s'il sait lequel des deux s'est produit (`2` = « j'ai refusé, l'état
/// local est intact », `1` = « j'ai échoué en route », convention posée en #2294).
///
/// Pourquoi un type neuf plutôt que `RegleMetierException`, que la CLI classe déjà en refus : sa
/// documentation dit qu'elle se distingue « de `DataAccessException`, qui enveloppe une panne
/// technique de persistance ». Réutiliser l'une pour l'autre brouillerait les deux notions. Il
/// manquait un troisième mot, et son nom porte l'invariant qui justifie le code 2.
///
/// Elle **reste** une [DataAccessException] : les appelants qui l'attrapent déjà continuent de la
/// voir passer.
public class RefusAvantEcriture extends DataAccessException {

    public RefusAvantEcriture(String message, Throwable cause) {
        super(message, cause);
    }

    /// Un refus qui n'enveloppe rien : la couche persistance a **décidé**, il n'y a pas de panne
    /// sous-jacente à montrer.
    public RefusAvantEcriture(String message) {
        super(message, null);
    }
}
