package fr.univ_amu.iut.commun.viewmodel;

import fr.univ_amu.iut.commun.model.Severite;
import java.util.List;
import java.util.Objects;

/// **Compte rendu d'une opération** : ce qui est revenu et sur quelle preuve, ce qui a été refusé et
/// pourquoi, ce qui manque et de quel manque il s'agit (ADR 0031).
///
/// À distinguer du [RetourOperation], qui dit un fait **borné** et clos (« Passage enregistré. ») et se
/// rend au bandeau. Un compte rendu est **extensible par nature** : il grandit avec ce qu'il y a à dire.
/// Celui de la réactivation a gagné deux sections en une seule session. Le loger dans un bandeau
/// reviendrait à le tronquer, donc à retirer ce qui permet à l'utilisateur de savoir quoi faire.
///
/// ## Une structure, pas un texte
///
/// Quatre écrans l'assemblaient au `StringBuilder`, chacun à sa façon. Le résultat tenait dans un `Label`
/// unique, ce qui interdit de styler une rubrique, d'en masquer une, ou de l'indenter (#1987 en découle
/// directement) - et oblige les tests à chercher des sous-chaînes dans un pavé.
///
/// La grammaire retenue est celle que ces écrans employaient déjà sans la nommer :
///
/// ```
/// Réactivation partielle                                   <- titre
///
/// Ce dossier ne contenait que vos enregistrements bruts…    <- préambule
///
/// 4229 séquence(s) réactivée(s) (identité vérifiée : forte) <- constat
/// 7 séquence(s) restent introuvables dans ce dossier        <- constat
///   • X.wav : enregistrement absent du dossier              <- détail
///   • Y.wav : tranche non régénérée                         <- détail
///
/// L'audio reste incomplet : 4229 séquence(s) sur 4236       <- conclusion
/// ```
///
/// ## Le plafond d'affichage n'est pas ici
///
/// Le modèle porte **tous** les détails. C'est la surface qui décide d'en montrer cinq puis de résumer
/// (une modale doit rester lisible) ou de tout rendre (une sortie de commande se filtre). Auparavant le
/// plafond était cuit dans la construction du texte, ce qui obligeait la CLI et l'IHM à dupliquer la mise
/// en forme pour diverger sur ce seul point - et à diverger sur le reste par accident.
///
/// @param titre ce que l'opération a produit, en une ligne (« Passage réactivé », « Réactivation partielle »)
/// @param preambule mise en contexte facultative, vide si l'opération n'en demande pas
/// @param constats les faits, dans l'ordre où ils doivent se lire
/// @param conclusion ce qu'il faut retenir, vide s'il n'y a rien à ajouter
public record CompteRendu(String titre, String preambule, List<Constat> constats, String conclusion) {

    public CompteRendu {
        Objects.requireNonNull(titre, "titre");
        Objects.requireNonNull(preambule, "preambule");
        Objects.requireNonNull(conclusion, "conclusion");
        constats = List.copyOf(constats);
    }

    /// Un compte rendu **sans préambule ni conclusion** : les constats se suffisent.
    public static CompteRendu de(String titre, List<Constat> constats) {
        return new CompteRendu(titre, "", constats, "");
    }

    /// Y a-t-il quelque chose à montrer ? Un compte rendu vide ne se rend pas : mieux vaut ne rien
    /// afficher qu'un cadre vide qui laisse croire à une opération sans effet.
    public boolean estVide() {
        return titre.isBlank() && preambule.isBlank() && constats.isEmpty() && conclusion.isBlank();
    }

    /// La sévérité **la plus forte** parmi les constats : c'est elle qui qualifie l'ensemble, faute de
    /// quoi un compte rendu comportant un échec se présenterait comme un succès.
    public Severite severite() {
        return constats.stream()
                .map(Constat::severite)
                .max(java.util.Comparator.comparingInt(Severite::ordinal))
                .orElse(Severite.INFO);
    }

    /// Un **fait** du compte rendu, et le détail qui l'accompagne éventuellement.
    ///
    /// Le fait se lit seul (« 7 séquence(s) restent introuvables dans ce dossier ») ; les détails
    /// l'instruisent, un par sujet concerné. Les deux sont séparés parce qu'une surface peut vouloir le
    /// fait sans les détails - c'est ce que fait la modale au-delà de cinq.
    ///
    /// @param fait la phrase, qui doit se comprendre sans les détails
    /// @param severite ce que ce fait vaut : un succès, une information, un échec
    /// @param details ce qui l'instruit, éventuellement vide
    public record Constat(String fait, Severite severite, List<Detail> details) {

        public Constat {
            Objects.requireNonNull(fait, "fait");
            Objects.requireNonNull(severite, "severite");
            details = List.copyOf(details);
        }

        /// Un constat **sans détail** : le fait se suffit.
        public static Constat de(String fait, Severite severite) {
            return new Constat(fait, severite, List.of());
        }
    }

    /// Le détail d'un constat : **de quoi** il s'agit, et **pourquoi**.
    ///
    /// Les deux sont séparés parce qu'ils ne jouent pas le même rôle : le sujet identifie (un nom de
    /// fichier, que l'utilisateur ira chercher), la précision explique (un motif, qui lui dit si la balle
    /// est dans son camp ou dans le nôtre). Les fondre en une chaîne les rendrait indissociables au rendu.
    ///
    /// @param sujet ce dont on parle, tel que l'utilisateur pourra le retrouver
    /// @param precision ce qu'il faut en savoir, vide si le sujet parle de lui-même
    public record Detail(String sujet, String precision) {

        public Detail {
            Objects.requireNonNull(sujet, "sujet");
            Objects.requireNonNull(precision, "precision");
        }

        public static Detail de(String sujet) {
            return new Detail(sujet, "");
        }
    }
}
