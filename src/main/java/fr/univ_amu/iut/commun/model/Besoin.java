package fr.univ_amu.iut.commun.model;

/// Ce qui **manque** pour qu'un geste soit possible (#2635), nommé plutôt que raconté.
///
/// Un refus a deux moitiés : **ce qui manque**, qui est un fait du domaine, et **comment y remédier**,
/// qui dépend de l'endroit d'où l'on parle. Les mélanger revient à écrire l'un pour une seule surface :
/// « connectez-vous (menu ☰ > Se connecter) » servait un menu à quelqu'un qui travaille dans un
/// terminal, et l'aurait servi demain à un script ou à une API.
///
/// Le modèle dit donc le besoin ; chaque surface ajoute son geste (`commun.viewmodel.GesteAttendu` pour
/// l'IHM, `cli.GesteAttenduCli` pour la ligne de commande). Personne ne perd de guidage : l'utilisateur
/// de l'IHM garde son chemin de menu, celui du terminal reçoit une commande.
///
/// Porté par [RegleMetierException#besoin()], donc **optionnel** : la plupart des refus n'expriment
/// aucun besoin d'environnement (« ce passage est déjà déposé ») et n'ont rien à faire ici.
public sealed interface Besoin {

    /// Ce qui manque, dit **sans nommer d'écran ni de commande**. Première phrase du message rendu.
    String enonce();

    /// La **connexion** à Vigie-Chiro : le geste lit ou écrit sur la plateforme.
    record Connexion() implements Besoin {

        @Override
        public String enonce() {
            return "la connexion à Vigie-Chiro est requise";
        }
    }

    /// Une **fonctionnalité désactivable** (#1057) dont le geste dépend.
    ///
    /// @param nom le nom tel qu'il apparaît à l'utilisateur, entre guillemets dans les réglages
    record Fonctionnalite(String nom) implements Besoin {

        @Override
        public String enonce() {
            return "la fonctionnalité « " + nom + " » est désactivée";
        }
    }
}
