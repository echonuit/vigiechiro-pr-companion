package fr.univ_amu.iut.commun.viewmodel;

import fr.univ_amu.iut.commun.model.Severite;
import java.util.List;
import java.util.Objects;

/// **Compte rendu chiffré** d'une opération lourde (#2358, chantier #2350) : ce qui est passé et ce qui a
/// été écarté, **en proportions** et non en listes, puis l'action suivante.
///
/// ## Pourquoi un second compte rendu
///
/// [CompteRendu] (ADR 0031) porte des **phrases** : un titre, des constats, un détail par sujet. Il répond
/// à « qu'est-ce qui s'est passé, précisément ». Ce modèle-ci porte des **quantités** : il répond à « dans
/// quelles proportions », question à laquelle une énumération ne répond pas. À la fin d'un import, la
/// liste des rejets dit *lesquels* ; seule une barre dit s'ils sont trois ou la moitié de la nuit. Les deux
/// coexistent, et une même surface peut montrer l'un puis l'autre.
///
/// ## Les trois règles, tenues par le modèle
///
/// 1. **Les proportions sont à l'échelle** : les largeurs se calculent sur les quantités réelles
///    ([Barre#fraction]), jamais sur une impression. Une barre qui ne respecte pas ce qu'elle représente
///    est pire qu'un tableau : elle donne une vue fausse avec l'autorité du visuel.
/// 2. **Un ensemble se ventile entièrement** : [Ventilation] **refuse** une somme de segments qui ne fait
///    pas son total. L'appelant est ainsi contraint de **nommer** le reliquat, là où un « autres »
///    silencieux masquerait précisément ce que l'utilisateur cherchait.
/// 3. **Le compte rendu ne se termine pas sur « Fermer »** : [#actions] porte ce qu'on fait ensuite.
///
/// Modèle **présentationnel pur** : il ne va rien chercher et n'appartient à aucune feature. Chaque
/// opération y projette ce qu'elle a déjà produit (rapport d'import, bilan de publication, rapport de
/// réactivation) ; aucune donnée n'est créée ici.
///
/// Chaque bloc est **facultatif** : vide, il ne se rend pas. Un compte rendu sans rejet n'affiche pas un
/// cadre vide intitulé « Motifs de rejet ».
///
/// @param titre l'opération et son objet, en une ligne (« Import terminé : nuit du 22/06/2026 »)
/// @param resultat le résultat **chiffré** de la pastille (« 583 / 612 importés ») : un libellé, jamais
///     une couleur seule - la couleur ne se lit pas quand on ne la distingue pas
/// @param severite ce que vaut l'ensemble, qui décide de l'habillage de la pastille
/// @param volumes barres comparées **à échelle commune** (lu / écrit), vide si l'opération n'en dit rien
/// @param ventilation le devenir d'un ensemble, exhaustif ; [Ventilation#aucune()] s'il n'y a rien à ventiler
/// @param motifs motifs de rejet et leurs effectifs, chacun ouvrant sa liste ; vide s'il n'y a aucun rejet
/// @param avertissements ce qui reste **vrai à la fin** de l'opération, chacun avec sa sévérité (#1488)
/// @param actions ce qu'on fait ensuite, la première étant mise en avant
public record CompteRenduChiffre(
        String titre,
        String resultat,
        Severite severite,
        List<Barre> volumes,
        Ventilation ventilation,
        List<Motif> motifs,
        List<Avertissement> avertissements,
        List<Action> actions) {

    /// Nom du paramètre `libelle`, que les cinq records imbriqués gardent tous : mutualisé pour ne pas
    /// répéter le même littéral (PMD `AvoidDuplicateLiterals`).
    private static final String LIBELLE = "libelle";

    /// Ce qui reste vrai à la fin, **avec sa sévérité**.
    ///
    /// La sévérité n'était pas là, et la première capture de la réactivation l'a montré : le composant
    /// posait un triangle d'alerte devant « L'audio est de nouveau complet : le passage est écoutable. »
    /// et devant un indice de concordance explicitement annoncé non bloquant. Un compte rendu qui alerte
    /// sur une bonne nouvelle apprend à ne plus regarder ses alertes.
    ///
    /// @param texte la phrase, écrite pour un humain
    /// @param severite ce qu'elle pèse : [Severite#INFO] pour un fait de contexte, [Severite#SUCCES] pour
    ///     une bonne nouvelle, [Severite#AVERTISSEMENT] pour ce sur quoi il faut revenir
    public record Avertissement(String texte, Severite severite) {

        public Avertissement {
            Objects.requireNonNull(texte, "texte");
            Objects.requireNonNull(severite, "severite");
        }

        /// Un avertissement au sens strict : ce sur quoi l'utilisateur devra revenir.
        public static Avertissement de(String texte) {
            return new Avertissement(texte, Severite.AVERTISSEMENT);
        }

        /// Un fait de contexte, ni bon ni mauvais : il informe, il n'alerte pas.
        public static Avertissement info(String texte) {
            return new Avertissement(texte, Severite.INFO);
        }

        /// Une bonne nouvelle : elle mérite d'être dite, pas d'être signalée.
        public static Avertissement succes(String texte) {
            return new Avertissement(texte, Severite.SUCCES);
        }
    }

    public CompteRenduChiffre {
        Objects.requireNonNull(titre, "titre");
        Objects.requireNonNull(resultat, "resultat");
        Objects.requireNonNull(severite, "severite");
        Objects.requireNonNull(ventilation, "ventilation");
        volumes = List.copyOf(volumes);
        motifs = List.copyOf(motifs);
        avertissements = List.copyOf(avertissements);
        actions = List.copyOf(actions);
    }

    /// Les seules phrases des mentions, sans leur registre : pour les surfaces qui n'ont pas d'habillage à
    /// choisir - une sortie texte, une assertion de test.
    public List<String> textesDesAvertissements() {
        return avertissements.stream().map(Avertissement::texte).toList();
    }

    /// **Échelle commune** des barres de volume : la plus grande quantité de l'ensemble. Deux barres
    /// comparées doivent partager leur référence, sinon « lu » et « écrit » paraissent égaux alors que
    /// l'un vaut le triple de l'autre.
    public long echelleDesVolumes() {
        return volumes.stream().mapToLong(Barre::total).max().orElse(0);
    }

    /// Résumé des motifs pour le pied (« 6 fichier déjà expansé, 2 en-tête WAV illisible »), vide s'il n'y
    /// a aucun motif : c'est ce qui permet de ne **pas** dérouler la liste des fichiers dans la bande, tout
    /// en disant déjà de quoi il s'agit.
    ///
    /// Le préfixe chiffré (« 8 rejetés : ») reste à la surface, qui connaît le mot juste pour l'opération -
    /// « rejetés » à l'import, « introuvables » à la réactivation.
    public String resumeDesMotifs() {
        return motifs.stream()
                .map(motif -> motif.compte() + " " + motif.libelle())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /// Nombre total de sujets couverts par les motifs : l'effectif que le préfixe du résumé annonce.
    public int nombreDeSujetsMotives() {
        return motifs.stream().mapToInt(Motif::compte).sum();
    }

    /// Une **barre** : un libellé, et les segments qui la composent (un seul pour un volume simple,
    /// plusieurs pour un volume ventilé - « écrit » = bruts + séquences).
    ///
    /// @param libelle ce que la barre mesure (« Carte SD », « Écrit »)
    /// @param segments ses parts, dans l'ordre d'affichage
    public record Barre(String libelle, List<Segment> segments) {

        public Barre {
            Objects.requireNonNull(libelle, LIBELLE);
            segments = List.copyOf(segments);
        }

        /// Barre d'un seul tenant.
        public static Barre unique(String libelle, Segment segment) {
            return new Barre(libelle, List.of(segment));
        }

        /// Somme des quantités de ses segments.
        public long total() {
            return segments.stream().mapToLong(Segment::quantite).sum();
        }

        /// Part d'un segment dans cette barre, entre 0 et 1 (0 si la barre est vide) : c'est **cette**
        /// valeur qui doit piloter une largeur, jamais une estimation.
        public double fraction(Segment segment) {
            long total = total();
            return total == 0 ? 0 : (double) segment.quantite() / total;
        }
    }

    /// Un **segment** de barre : sa quantité (qui pilote la géométrie) et sa valeur **lisible** (que le
    /// modèle ne fabrique pas : « 5,0 Go » ou « 612 fichiers » dépend du domaine, pas de la surface).
    ///
    /// La valeur lisible est **toujours** portée, même pour un segment minuscule : c'est ce qui garantit
    /// qu'un petit segment reste chiffré en légende plutôt qu'arrondi en silence à zéro.
    ///
    /// @param libelle ce que le segment représente (« Importés », « séquences »)
    /// @param quantite la quantité réelle, pour l'échelle
    /// @param valeurLisible la quantité telle qu'elle se lit, unité comprise
    /// @param teinte le **rôle** du segment, dont la feuille de style déduit la couleur
    public record Segment(String libelle, long quantite, String valeurLisible, Teinte teinte) {

        public Segment {
            Objects.requireNonNull(libelle, LIBELLE);
            Objects.requireNonNull(valeurLisible, "valeurLisible");
            Objects.requireNonNull(teinte, "teinte");
            if (quantite < 0) {
                throw new IllegalArgumentException("quantité négative pour « " + libelle + " » : " + quantite);
            }
        }
    }

    /// **Rôle** d'un segment, d'où la feuille de style tire sa couleur.
    ///
    /// Un rôle et non une couleur, ni un nom de classe CSS : le modèle dit ce que le segment **est** dans
    /// le compte rendu, la surface décide de son apparence. C'est aussi ce qui permet au thème sombre de
    /// réinterpréter la palette sans toucher aux appelants.
    ///
    /// Les trois premiers rôles qualifient un **devenir** (ce qui est passé, écarté, refusé) ; les trois
    /// suivants qualifient un **volume**, où aucune sévérité n'a de sens : sur la barre « écrit », les
    /// bruts conservés et les séquences produites ne sont ni bons ni mauvais, ils se distinguent.
    public enum Teinte {
        /// Ce qui est passé (importé, déposé, réactivé).
        RETENU,
        /// Ce qui a été écarté sans que ce soit un échec (déjà présent, non pertinent).
        ECARTE,
        /// Ce qui a été refusé ou a échoué.
        REFUSE,
        /// Une quantité de référence, sans jugement (ce qui a été lu).
        REFERENCE,
        /// La part principale d'un volume écrit (les bruts conservés).
        PRINCIPALE,
        /// La part secondaire d'un volume écrit (les séquences produites).
        ///
        /// ⚠ Elle **partage la couleur** de [#RETENU], parce qu'elle est la seconde part d'un couple de
        /// même nature - « bruts + séquences » se lit comme un tout. L'employer dans une **ventilation**,
        /// où chaque part a un sens distinct, fait lire cette part comme une réussite : c'est arrivé au
        /// branchement du dépôt, où « sans ancrage » ressortait du même vert que « publiées ».
        SECONDAIRE
    }

    /// Le **devenir d'un ensemble**, ventilé **entièrement**.
    ///
    /// L'exhaustivité est un **invariant de construction**, pas une vérification tardive : une somme de
    /// segments qui ne fait pas le total est refusée. L'appelant doit donc **nommer** ce qui reste - c'est
    /// tout l'objet de la règle, un reliquat anonyme masquant ce qu'on cherchait.
    ///
    /// @param libelle ce que l'ensemble contient (« Devenir des 612 enregistrements »)
    /// @param total l'effectif total de l'ensemble
    /// @param segments ses parts ; leur somme **doit** faire `total`
    public record Ventilation(String libelle, long total, List<Segment> segments) {

        public Ventilation {
            Objects.requireNonNull(libelle, LIBELLE);
            segments = List.copyOf(segments);
            if (total < 0) {
                throw new IllegalArgumentException("total négatif : " + total);
            }
            long somme = segments.stream().mapToLong(Segment::quantite).sum();
            if (!segments.isEmpty() && somme != total) {
                throw new IllegalArgumentException("ventilation « " + libelle + " » non exhaustive : les segments"
                        + " font " + somme + " pour un total de " + total + ". Nommez le reliquat (" + (total - somme)
                        + ") au lieu de le laisser muet.");
            }
        }

        /// Ventilation **absente** : rien à ventiler, le bloc ne se rendra pas.
        public static Ventilation aucune() {
            return new Ventilation("", 0, List.of());
        }

        /// Rien à montrer (aucun segment) : la surface masque le bloc.
        public boolean estVide() {
            return segments.isEmpty();
        }

        /// Part d'un segment, entre 0 et 1 (0 si l'ensemble est vide).
        public double fraction(Segment segment) {
            return total == 0 ? 0 : (double) segment.quantite() / total;
        }

        /// Pourcentage d'un segment pour la légende, arrondi **au dixième** - jamais à l'unité. Sur un
        /// import à 583 / 21 / 8, l'unité donnerait « 95 + 3 + 1 = 99 % », et un compte rendu qui ne fait
        /// pas 100 % laisse chercher le point manquant. L'arrondi ne concerne que la légende : la
        /// géométrie garde la fraction exacte ([#fraction]).
        ///
        /// Le dixième **réduit** le défaut de somme, il ne l'annule pas dans l'absolu (trois parts égales
        /// donnent 33,3 × 3 = 99,9). Aucune promesse de total exact n'est donc faite ici.
        public double pourcentage(Segment segment) {
            return Math.round(fraction(segment) * 1000) / 10.0;
        }
    }

    /// Un **motif** de rejet et son effectif, avec les sujets concernés : le détail reste accessible, il
    /// n'est simplement plus la première chose qu'on lit.
    ///
    /// @param libelle la raison, écrite pour un humain
    /// @param sujets ce qui est concerné (noms de fichiers), tel que l'utilisateur le retrouvera
    public record Motif(String libelle, List<String> sujets) {

        public Motif {
            Objects.requireNonNull(libelle, LIBELLE);
            sujets = List.copyOf(sujets);
        }

        /// Effectif du motif : le nombre de sujets qu'il porte.
        public int compte() {
            return sujets.size();
        }
    }

    /// Ce qu'on **fait ensuite**. Un compte rendu qui se termine sur « Fermer » laisse l'utilisateur
    /// devant la question qu'il se posait.
    ///
    /// @param libelle le geste proposé (« Ouvrir le passage », « Retenter les échecs »)
    /// @param principale `true` pour le geste mis en avant (un seul par compte rendu)
    /// @param geste ce qu'il déclenche
    public record Action(String libelle, boolean principale, Runnable geste) {

        public Action {
            Objects.requireNonNull(libelle, LIBELLE);
            Objects.requireNonNull(geste, "geste");
        }
    }
}
