#!/usr/bin/env python3
"""Auto-test des scripts de vérification ADR (issue #2467).

Ce que le portail qualité gardait déjà : qu'un script ne PLANTE pas et que son compte ne DÉPASSE pas le
cliquet (sur-comptage). Ce qu'il ne gardait pas : qu'un script **détecte** vraiment. Un script qui, par
régression, cesserait de voir un motif ferait BAISSER son compte sous le cliquet - le portail resterait
vert pendant qu'une vraie violation passe. Un faux vert, exactement ce que le mécanisme combat.

Chaque cas plante une violation CONNUE dans une fixture et exige que le script la voie (test positif) ;
et plante la MÊME chose en COMMENTAIRE, exigeant qu'il ne la compte pas (le défaut exact de la passe 1
de clôture, où trois scripts comptaient les commentaires). Le premier ferme le trou de déflation, le
second interdit le retour de la cécité aux commentaires.

Aucune dépendance hors stdlib : lancé comme les scripts, `python3 scripts/adr/verifie_scripts.py`.
"""

import importlib.util
import pathlib
import sys
import tempfile

ICI = pathlib.Path(__file__).parent

# Nomme parce qu il porte PLUSIEURS cas la ou ses voisins n en ont qu un : le cliquet Java, la
# tolerance zero d une zone, le refus d une zone qui ne balaie rien, le discernement de la couverture,
# le balayage non recursif. Sans denombrement ici : la phrase a deja derive deux fois en une journee,
# et un commentaire qui compte ses voisins vieillit a chaque ajout.
ADR_2843 = "2843-tiret-cadratin.py"

_echecs: list[str] = []


# Les scripts que ce harnais a REELLEMENT charges pendant la passe. C'est la source du controle de
# completude : une liste declaree serait un second inventaire a tenir, c'est-a-dire le defaut qu'on
# corrige. Elle se remplit au fil des cas, donc un `test_*` defini mais jamais joue laisse son
# detecteur decouvert - et le dit.
_charges: set[str] = set()


def _charge(nom: str):
    """Importe un script au nom non-importable (chiffres, tirets) par son chemin."""
    _charges.add(nom)
    module = "adr_" + nom.replace("-", "_").replace(".py", "")
    spec = importlib.util.spec_from_file_location(module, ICI / nom)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def _verifie(cas: str, obtenu, attendu) -> None:
    if obtenu == attendu:
        print(f"  ✔ {cas}")
    else:
        _echecs.append(cas)
        print(f"  ✘ {cas} : attendu {attendu}, obtenu {obtenu}")


def _ecrire(racine: pathlib.Path, chemin_relatif: str, contenu: str) -> None:
    f = racine / chemin_relatif
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(contenu, encoding="utf-8")


def test_0008_echec_silencieux() -> None:
    m = _charge("0008-echec-silencieux.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "src/main/java/Exemple.java",
            "class E {\n"
            "  void a() { try { x(); } catch (Exception e) { } }\n"  # vide -> compte
            "  void b() { try { x(); } catch (Exception e) { log(e); } }\n"  # tracé -> non
            "  void c() { try { x(); } catch (Exception e) { /* ignoré */ } }\n"  # commentaire seul -> compte
            "  /* void mort() { try{}catch(E e){} } */\n"  # catch EN commentaire -> non
            "}\n",
        )
        # L arbre de TEST est dans la meme population, et ce cas est ce qui le tient. Le compte de 3
        # separe les deux arbres : retirer `TESTS` de `RACINES` le ramenerait a 2, et le temoin
        # rougirait. Un temoin pose sur la seule production aurait laisse passer ce retrait en
        # silence, ce qui est exactement le faux vert que cette suite existe pour interdire.
        _ecrire(
            racine,
            "src/test/java/ExempleTest.java",
            "class ET { void d() { try { x(); } catch (Exception e) { } } }\n",
        )
        n = len(m.suspects(racine=racine))
        _verifie("0008 détecte les catch vides dans les DEUX arbres, production et test", n, 3)


def test_3053_capture_libelle() -> None:
    """Le cas discriminant est le troisième : la MÊME écriture, hors d'un outil de capture, ne compte pas.

    Sans lui, le motif attraperait tout `ifPresent` du dépôt, deviendrait du bruit, et finirait
    désactivé - ce qui coûte plus cher que l'absence de cliquet.
    """
    m = _charge("3053-capture-libelle.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        abstention = (
            "  void geste() {\n"
            "    menu.getItems().stream().filter(i -> \"Lieu\".equals(i.getText()))\n"
            "        .findFirst().ifPresent(MenuItem::fire);\n"
            "  }\n"
        )
        _ecrire(racine, "audio/outils/CaptureX.java", "class CaptureX {\n" + abstention + "}\n")
        # Le remède : exige et lève. Ne compte pas.
        _ecrire(
            racine,
            "audio/outils/CaptureY.java",
            "class CaptureY {\n"
            "  void geste() {\n"
            "    ApercuFx.exigerParLibelle(\"le menu\", menu.getItems(), MenuItem::getText, \"Lieu\").fire();\n"
            "  }\n"
            "}\n",
        )
        # Le motif CITÉ dans un commentaire (c'est le cas de l'en-tête d'ApercuFx) : ne compte pas.
        _ecrire(
            racine,
            "audio/outils/CaptureZ.java",
            "class CaptureZ {\n  /* proscrit : .findFirst().ifPresent(x) */\n}\n",
        )
        # Ni le nom ni le paquet d'un outil de capture : hors périmètre, l'usage y est normal.
        _ecrire(racine, "audio/view/UnControleur.java", "class UnControleur {\n" + abstention + "}\n")
        _ecrire(racine, "audio/model/CaptureAilleurs.java", "class CaptureAilleurs {\n" + abstention + "}\n")
        n = len(m.suspects(sources=racine))
        _verifie("3053 détecte le geste qui ne se fait pas, et lui seul", n, 1)


def test_0010_dialogue_hors_port() -> None:
    m = _charge("0010-dialogue-hors-port.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Vue.java",
            "class Vue {\n"
            "  void go() { new Alert(Alert.AlertType.ERROR).showAndWait(); }\n"  # code -> compte
            "  /// jadis un `Alert.showAndWait()` figeait le test\n"  # commentaire -> non
            "}\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("0010 détecte un Alert hors port, ignore le commentaire", n, 1)


def test_0035_pictogramme() -> None:
    m = _charge("0035-pictogramme-caractere.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Vue.fxml",
            '<VBox>\n'
            '  <Button text="\U0001f5d1 Supprimer"/>\n'  # pictogramme dans un libellé -> compte
            '  <!-- \U0001f5d1 en commentaire, prose autorisée -->\n'  # commentaire -> non
            "</VBox>\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("0035 détecte un pictogramme, ignore le commentaire", n, 1)


def test_2843_tiret_cadratin() -> None:
    m = _charge(ADR_2843)
    cad = chr(0x2014)
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Exemple.java",
            "class Exemple {\n"
            "    // un tiret cadratin " + cad + " en commentaire, et la regle vise les commentaires\n"
            '    String propre = "deux-points : voila la forme attendue";\n'  # rien -> non
            '    static final String ABSENTE = "' + cad + '";\n'  # litteral = le glyphe -> non
            "    /// La cellule vide affiche `" + cad + "` dans le tableau.\n"  # chevrons -> non
            "    /// Le verdict vaut « " + cad + " a verifier » tant que rien n est pose.\n"  # cite -> non
            '    Pattern LEGACY = Pattern.compile("probable [-' + cad + '] `");\n'  # classe litterale -> non
            "}\n",
        )
        n = len(m.suspects(racine))
        # Contrairement a ses voisins, ce script COMPTE les commentaires : la regle porte sur « la doc
        # et les commentaires », qui sont ici la matiere et non le bruit. Les quatre formes CITEES, en
        # revanche, ne sont pas de la prose (meme regle que les zones Markdown) : le glyphe defini en
        # litteral, entre chevrons de code, dans un libelle recopie, ou dans la classe de caracteres
        # par laquelle un analyseur accepte l ancienne forme. Ce cas garde les deux sens a la fois - si
        # le motif de citation devenait gourmand il avalerait la prose et n tomberait a 0 ; s il cessait
        # de proteger, n monterait a 5 et le cliquet buterait sur un plancher fantome.
        _verifie("2843 compte la prose Java, epargne les quatre formes citees", n, 1)


def test_2843_prose_documentation() -> None:
    m = _charge(ADR_2843)
    cad = chr(0x2014)
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "page.md",
            "Une phrase " + cad + " avec un cadratin de prose.\n"  # prose -> compte
            "La cellule vide affiche `" + cad + "` dans le tableau.\n"  # chevrons de code -> non
            "Le lien « GPS manquant " + cad + " placer » vient de l application.\n"  # citation -> non
            "Un motif `[-" + cad + "]` accepte l ancienne forme d en-tete.\n"  # classe litterale -> non
            "Voir [le guide " + cad + " chapitre 3](guide.md) pour la suite.\n"  # LIEN Markdown -> compte
            "Une phrase saine : deux-points.\n",  # rien -> non
        )
        n = len(m.prose(racine))
        # La regle « ce qui est cite n est pas de la prose » couvre le glyphe ET les libelles de
        # l application. Si le motif de citation devenait trop gourmand, il avalerait la prose et ce
        # cas tomberait a zero : c est exactement la deflation que ce fichier existe pour interdire.
        #
        # Le LIEN Markdown est ici pour une raison precise. La forme citee « classe de caracteres » est
        # ecrite en LITTERAL, et non « des crochets contenant un cadratin », faute de quoi elle
        # avalerait tout libelle de lien portant un tiret de prose. Ce fichier de fixture contient donc
        # les deux : la classe, epargnee, et le lien, compte. Elargir le motif fait tomber n a 1.
        _verifie("2843 zone nettoyee : compte la prose et les liens, epargne les citations", n, 2)


def test_2843_zone_vide_est_une_erreur() -> None:
    """Une zone qui ne balaie AUCUN fichier doit lever, pas rapporter zero.

    C est le faux vert le plus difficile a voir de tout ce fichier : un motif mal apparie a son arbre
    (« *.md » sur un arbre Java) fait dire au garde « 0 cadratin de prose », ce qui a la forme exacte
    du succes. Rien, ailleurs, ne distingue une zone propre d une zone jamais regardee.
    """
    m = _charge(ADR_2843)
    cad = chr(0x2014)
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(racine, "Source.java", "// une prose " + cad + " avec un cadratin\n")
        # Le bon motif voit le fichier et compte sa prose.
        _verifie("2843 zone nettoyee : le motif « *.java » voit l arbre Java", len(m.prose(racine, (), "*.java")), 1)
        # Le mauvais motif ne voit rien : ce doit etre une erreur, jamais un zero rassurant.
        try:
            m.prose(racine, (), "*.md")
        except AssertionError:
            _verifie("2843 zone nettoyee : un motif qui ne balaie rien leve", 1, 1)
        else:
            _verifie("2843 zone nettoyee : un motif qui ne balaie rien leve", 0, 1)


def test_2843_couverture_distingue_dedans_dehors() -> None:
    """`couvert()` doit DISTINGUER, pas repondre oui a tout.

    Le regime de couverture repond « quel fichier personne ne regarde ? ». Il ne peut pas garder son
    propre discernement : rendre `couvert()` toujours vrai l AVEUGLE, et un detecteur aveugle rapporte
    « 0 fichier sans garde », soit exactement le vert de la bonne sante. Muter le script ne fait donc
    pas rougir le script. Ce cas-ci est le seul endroit ou cette mutation se voit.
    """
    m = _charge(ADR_2843)
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(racine, "zone/dedans.md", "peu importe\n")
        _ecrire(racine, "ailleurs/dehors.md", "peu importe\n")
        zones, sources = m.ZONES_NETTOYEES, m.SOURCES
        try:
            m.ZONES_NETTOYEES = (("zone temoin", racine / "zone", (), "*.md"),)
            m.SOURCES = []
            _verifie("2843 couverture : un fichier DANS la zone est couvert",
                     m.couvert(racine / "zone" / "dedans.md"), True)
            _verifie("2843 couverture : un fichier HORS zone ne l est pas",
                     m.couvert(racine / "ailleurs" / "dehors.md"), False)
        finally:
            m.ZONES_NETTOYEES, m.SOURCES = zones, sources


def test_2843_balayage_non_recursif() -> None:
    """`recursif=False` doit vraiment s arreter au niveau de la racine.

    Sans ce cas, le drapeau serait un no-op SILENCIEUX : la zone racine se mettrait a descendre dans
    tout le depot, ramasserait les fichiers non suivis, et ferait rougir le garde chez le developpeur
    sans rien signaler en CI. Un drapeau qu on croit actif et qui ne fait rien est pire qu absent.
    """
    m = _charge(ADR_2843)
    cad = chr(0x2014)
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(racine, "RACINE.md", "Une prose " + cad + " a la racine.\n")
        _ecrire(racine, "sousdossier/PROFOND.md", "Une prose " + cad + " en profondeur.\n")
        _verifie("2843 zone nettoyee : recursif voit les deux niveaux", len(m.prose(racine, (), "*.md")), 2)
        _verifie(
            "2843 zone nettoyee : non recursif s arrete a la racine",
            len(m.prose(racine, (), "*.md", False)),
            1,
        )


def test_0037_slot_actions() -> None:
    m = _charge("0037-slot-actions-hbox.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Vue.fxml",
            "<VBox>\n"
            '  <HBox styleClass="barre-actions"/>\n'  # slot d'actions en HBox -> compte
            '  <!-- <HBox styleClass="barre-actions"/> en commentaire -->\n'  # commentaire -> non
            "</VBox>\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("0037 détecte un slot d'actions HBox, ignore le commentaire", n, 1)


def test_2493_modale_suit_croissance() -> None:
    m = _charge("2493-modale-suit-croissance.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Une modale qui révèle un bandeau sans câbler suivreLaCroissance -> suspect.
        _ecrire(
            racine,
            "SuspectModaleController.java",
            "class SuspectModaleController { void init() { BandeauRetour.installer(a, b); } }\n",
        )
        # Une modale qui révèle ET câble -> pas suspect.
        _ecrire(
            racine,
            "SaineModaleController.java",
            "class SaineModaleController { void init() {\n"
            "  BandeauRetour.installer(a, b);\n"
            "  Modales.suivreLaCroissance(racine, b.managedProperty());\n"
            "} }\n",
        )
        # Une vue qui n'est pas une modale (nom sans « Modale ») -> hors champ, même si elle révèle.
        _ecrire(
            racine,
            "AnalyseController.java",
            "class AnalyseController { void init() { BandeauRetour.installer(a, b); } }\n",
        )
        n = len(m.suspects(vues=racine))
        _verifie("2493 détecte la modale non câblée, épargne la câblée et la non-modale", n, 1)


def test_loupe_0020() -> None:
    m = _charge("loupe-0020-ecritures-plateforme.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Client.java",
            "class Client {\n"
            "  public Reponse creerParticipation(String s) { return null; }\n"  # écriture -> compte
            "  public Reponse lireParticipation(String s) { return null; }\n"  # lecture -> non
            "}\n",
        )
        n = len(m.candidats(api=racine))
        _verifie("loupe 0020 liste les écritures, pas les lectures", n, 1)


def test_loupe_0044() -> None:
    m = _charge("loupe-0044-mecanisme-parallelisme.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Service.java",
            "class Service {\n"
            "  void go() { Thread.ofVirtual().start(r); }\n"  # mécanisme réel -> compte
            "  /// autrefois un Thread.ofVirtual() maison, remplacé par ExecuteurTache\n"  # cité en Javadoc -> non
            "}\n",
        )
        n = len(m.candidats(racine=racine))
        _verifie("loupe 0044 voit un mécanisme réel, ignore le Javadoc qui le cite", n, 1)


def test_2635_refus_sans_surface() -> None:
    m = _charge("2635-refus-sans-surface.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "fr/univ_amu/iut/site/model/Refus.java",
            "class Refus {\n"
            '  String a() { return "Connectez-vous depuis le menu \u2630"; }\n'
            '  String b() { return "Connectez-vous depuis le menu principal"; }\n'
            '  // autrefois : return "ouvrez le menu \u2630";\n'
            "}\n",
        )
        # Hors `**/model/**`, le glyphe est legitime : la surface a le droit de se nommer. Sans ce
        # second fichier, un script qui aurait perdu son filtre de zone resterait vert.
        _ecrire(
            racine,
            "fr/univ_amu/iut/site/view/Ecran.java",
            "class Ecran {\n" '  String a() { return "menu \u2630"; }\n' "}\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("2635 voit le glyphe dans un modèle, ignore le commentaire et la vue", n, 1)


def test_3947_message_enveloppe() -> None:
    m = _charge("3947-message-enveloppe.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Les TROIS formes que l'ADR nomme, une par ligne. Un compte exact rattrape la regression
        # d'une SEULE d'entre elles - ce qu'un temoin unique laisserait passer.
        _ecrire(
            racine,
            "fr/univ_amu/iut/commun/Enveloppe.java",
            "class Enveloppe {\n"
            "  String a(Exception e) { return e.getMessage() != null ? e.getMessage() : e.toString(); }\n"
            "  String b(Exception e) { return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(); }\n"
            '  String c(Throwable t) { return t.getCause() != null ? t.getCause().getMessage() : "?"; }\n'
            "  String d(Exception e) { return CauseLisible.de(e); }\n"
            "  // ancien : e.getMessage() != null ? e.getMessage() : e.toString();\n"
            "}\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("3947 voit les trois formes, ignore le remède et le commentaire", n, 3)


def test_4359_javadoc_narratif() -> None:
    m = _charge("4359-javadoc-narratif.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Quatre fixtures pour quatre comportements, et un compte EXACT qui les separe. Un temoin
        # unique dirait « il detecte » sans dire ce qu il epargne, or c est l epargne qui se casse :
        # compter les etiquettes de contrat comme de la prose penaliserait un record bien documente,
        # ce que l ADR refuse explicitement.
        #
        # Le QUATRIEME cas est celui du seuil PAR NATURE, et il est le plus fragile : le meme bloc,
        # au meme nombre de lignes, doit compter au-dessus d une methode et ne rien couter au-dessus
        # d une classe. Un seuil redevenu unique le ferait rougir des deux cotes.
        sur_type = m.SEUILS["type"]
        sur_methode = m.SEUILS["methode"]
        long_ = "\n".join(f"/// Ligne {i}." for i in range(sur_type + 3))
        court = "\n".join(f"/// Ligne {i}." for i in range(sur_type))
        contrat = "\n".join(f"/// @param p{i} le parametre {i}" for i in range(sur_type + 5))
        entre_deux = "\n".join(f"/// Ligne {i}." for i in range(sur_methode + 2))
        _ecrire(racine, "fr/univ_amu/iut/a/Bavard.java", long_ + "\nclass Bavard {}\n")
        _ecrire(racine, "fr/univ_amu/iut/a/Sobre.java", court + "\nclass Sobre {}\n")
        _ecrire(racine, "fr/univ_amu/iut/a/Contrat.java", contrat + "\nclass Contrat {}\n")
        _ecrire(racine, "fr/univ_amu/iut/a/SousClasse.java", entre_deux + "\nclass SousClasse {}\n")
        n = len(m.suspects(racine=racine))
        _verifie("4359 compte au-dela du seuil, epargne le bloc court et les etiquettes", n, 3)

        # Le MEME bloc, pose au-dessus d une methode : il coute, la ou il ne coutait rien au-dessus
        # d une classe. C est tout ce que le seuil par nature apporte, et rien d autre ne le tient.
        _ecrire(racine, "fr/univ_amu/iut/a/SousClasse.java",
                "class SousClasse {\n" + entre_deux + "\n    void faire() {}\n}\n")
        n = len(m.suspects(racine=racine))
        _verifie("4359 compte au-dessus d une methode ce qu il epargne au-dessus d une classe", n, 5)


def test_4366_avertissement_en_pictogramme() -> None:
    m = _charge("4366-avertissement-en-pictogramme.py")
    A = chr(0x26A0) + chr(0xFE0F)
    # Le detecteur est trivial, son EPARGNE ne l est pas : c est la qu il se trompe s il se trompe.
    # Un cas par cecite declaree en tete du script, plus le cas positif qui les rend non vides.
    cas = [
        ("prose : le signe alerte", f"{A} ne pas redemarrer entre deux passages.", ".md", False, 1),
        ("cite entre accents graves", f"les libelles commencaient par un `{A}`.", ".md", False, 0),
        ("cite entre guillemets francais", f"le signe « {A} » ouvrait la ligne.", ".md", False, 0),
        ("voisin d un autre marqueur", f"un \u2717 interdit ; un {A} laisse deposer.", ".md", False, 0),
        ("dans un bloc de code markdown", f"{A} sortie du programme", ".md", True, 0),
        ("chaine litterale d un fichier de code", f'echo "{A} rien n a ete filme"', ".sh", False, 0),
        ("noeud montre d une maquette", f"<text x=\"10\">{A} attention</text>", ".svg", False, 0),
    ]
    for titre, ligne, suffixe, bloc, attendu in cas:
        _verifie(f"4366 {titre}", len(m.alertes(ligne, suffixe, bloc)), attendu)


def test_4366_pictogramme_en_tete_de_ligne() -> None:
    """LE cas que la regle des « delimiteurs de chaque cote » laissait passer.

    Un avertissement s ecrit `⚠️ **texte**`, et c est la forme la plus courante du depot. Le cote
    gauche est vide - ce que l ancienne regle acceptait comme un delimiteur - et le `*` de l emphase
    fournissait le cote droit. 264 avertissements reels echappaient ainsi au compte. Une mention est
    ENCADREE : le meme delimiteur ouvre et ferme.
    """
    m = _charge("4366-avertissement-en-pictogramme.py")
    alerte = m.ALERTE if isinstance(m.ALERTE, str) else "\u26a0\ufe0f"
    import re as _re

    def compte(ligne, suffixe=".md"):
        pos = _re.search(m.ALERTE, ligne)
        return 0 if pos is None else len(m.alertes(ligne, suffixe, False))

    _verifie("4366 en tete de ligne, suivi de gras : compte", compte(f"{alerte} **Attention.**"), 1)
    _verifie("4366 en javadoc, suivi de gras : compte", compte(f"/// {alerte} **Attention.**", ".java"), 1)
    _verifie("4366 encadre de guillemets francais : epargne", compte(f"Le signe « {alerte} » se cite."), 0)
    _verifie("4366 encadre de parentheses : epargne", compte(f"Le pictogramme ({alerte}) est cite."), 0)


def test_4368_apostrophe_en_libelle() -> None:
    m = _charge("4368-apostrophe-en-libelle.py")
    C = chr(0x2019)
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # La regle est « rien que l ASCII, partout » : le commentaire compte autant que la chaine.
        # Ce que le garde doit EPARGNER, ce sont les lignes qui NOMMENT le caractere, et c est la
        # qu il se tromperait. Un compte de 2 separe les deux emplois des trois mentions ; un compte
        # de 5 voudrait dire qu il a cesse de faire la difference.
        _ecrire(
            racine,
            "Ecran.java",
            "class Ecran {\n"
            f'  String a() {{ return "l{C}audio est parti"; }} // l{C}appel vient d ailleurs\n'
            f"  // on ecrit `{C}` ou l ASCII, jamais les deux\n"
            f'  static final String COURBE = "{C}";\n'
            f'  static final String CLASSE = "[\'{C}]";\n'
            "}\n",
        )
        _verifie("4368 compte les emplois, epargne les mentions",
                 len(m.suspects(racine=racine)), 2)


def test_4395_renvois_en_javadoc() -> None:
    m = _charge("4395-renvois-en-javadoc.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Le detecteur compte ; ce qui se casse, c est son EPARGNE. Une couleur CSS a six chiffres
        # etait comptee comme un renvoi `#4` avant que la borne haute ne soit posee, et le corpus en
        # portait une. Un compte de 3 separe les trois issues citees des cinq pieges - couleur, lien
        # javadoc vers un membre, commentaire d implementation, zone de test, et le DOUBLON, qui
        # pointe la meme discussion et ne compte donc pas deux fois (#4398). Un compte de 7 voudrait
        # dire que le garde a cesse de faire la difference.
        _ecrire(
            racine,
            "src/main/java/fr/univ_amu/iut/a/Contrat.java",
            "/// Le decoupage suit #504, et le cas limite vient de #33.\n"
            "/// La teinte de fond vaut #123456 dans la palette.\n"
            "/// Voir {@link Decoupage#applique} pour le detail.\n"
            "/// Le verrou de navigation vient de #54.\n"
            "/// Le decoupage, encore lui (#504) : le doublon compte pour un.\n"
            "// vient de #4040, mais en commentaire d implementation\n"
            "class Contrat {}\n",
        )
        _ecrire(
            racine,
            "src/test/java/fr/univ_amu/iut/a/ContratTest.java",
            "/// Eprouve #4040, hors champ declare.\nclass ContratTest {}\n",
        )
        _verifie(
            "4395 compte les issues citees, epargne la couleur, le lien, le commentaire, la zone "
            "de test et le doublon",
            m.renvois(racine),
            3,
        )


def test_4359_blocs_relus() -> None:
    m = _charge("4359-blocs-relus.py")
    corpus = {"aaaa": "src/main/java/A.java", "bbbb": "src/main/java/B.java"}

    _verifie("4359 registre : une entree qui correspond n est pas perimee",
             len(m.perimees([("aaaa", "src/main/java/A.java", "motif")], corpus)), 0)
    _verifie("4359 registre : une entree qui ne correspond a rien est perimee",
             len(m.perimees([("zzzz", "src/main/java/Fantome.java", "motif")], corpus)), 1)
    _verifie("4359 registre : un registre vide ne perime rien",
             len(m.perimees([], corpus)), 0)
    # L EMPREINTE fait l identite, pas le chemin : un bloc deplace d un fichier a l autre reste relu.
    # C est un choix, et il est ici pour qu on le voie plutot que de le decouvrir.
    _verifie("4359 registre : l empreinte fait l identite, pas le chemin",
             len(m.perimees([("aaaa", "src/main/java/Ailleurs.java", "motif")], corpus)), 0)

    # Une REINDENTATION ne doit pas invalider une lecture qui reste valable.
    bloc = ["    /// Premiere ligne.", "    /// Seconde ligne."]
    decale = ["        /// Premiere ligne.", "        /// Seconde ligne."]
    _verifie("4359 registre : une reindentation ne change pas l empreinte",
             m.empreinte(bloc), m.empreinte(decale))
    # Mais une EDITION, si : c est tout le propos.
    _verifie("4359 registre : un mot change invalide l empreinte",
             m.empreinte(bloc) != m.empreinte(["    /// Premiere ligne.", "    /// Autre chose."]), True)


def test_loupe_4359_javadoc_vieillie() -> None:
    m = _charge("loupe-4359-javadoc-vieillie.py")
    # La loupe importe desormais ses seuils du cliquet, par nature. Les fixtures surmontent une
    # classe, donc c est le seuil `type` qui les departage.
    seuil = m.SEUILS["type"]
    long_ = [f"/// Ligne {i}." for i in range(seuil + 1)]
    court = [f"/// Ligne {i}." for i in range(seuil - 1)]
    corps = ["class A {}", ""]

    # Le cas positif : bloc sous cliquet, code plus recent que lui.
    lignes = long_ + corps
    temps = [100] * len(long_) + [200, 200]
    _verifie("loupe 4359 voit un bloc dont le code a bouge apres lui",
             len(m.candidats_du_fichier("A.java", lignes, temps)), 1)

    # Le code est PLUS ANCIEN : la javadoc a ete corrigee depuis, rien a signaler.
    _verifie("loupe 4359 epargne un bloc plus recent que son code",
             len(m.candidats_du_fichier("A.java", lignes, [200] * len(long_) + [100, 100])), 0)

    # Meme date : c est plus de la moitie du corpus, et la loupe est aveugle a ce cas - declare.
    _verifie("loupe 4359 epargne un bloc du meme commit que son code",
             len(m.candidats_du_fichier("A.java", lignes, [100] * (len(long_) + 2))), 0)

    # Sous le seuil : le bloc n est pas dans le cliquet, donc pas dans la surface de revue.
    _verifie("loupe 4359 epargne un bloc court, meme avec du code plus recent",
             len(m.candidats_du_fichier("A.java", court + corps, [100] * len(court) + [200, 200])), 0)

    # Une ligne VIDE plus recente ne compte pas : sans cela un simple retour a la ligne suffirait.
    _verifie("loupe 4359 ne compte pas une ligne vide comme du code",
             len(m.candidats_du_fichier("A.java", long_ + ["", "class A {}"],
                                        [100] * len(long_) + [999, 100])), 0)


def test_4472_commentaire_en_corps() -> None:
    m = _charge("4472-commentaire-en-corps.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Le cas qui compte : le MEME bloc de commentaires, hors d un corps de methode puis dedans.
        # Hors d un corps, c est de l en-tete de fichier et ce cliquet n a rien a en dire ; dedans,
        # c est la population qu il tient. Un garde qui confondrait les deux compterait les licences.
        bloc = "\n".join(f"        // Ligne {i}." for i in range(m.SEUIL + 3))
        _ecrire(racine, "fr/univ_amu/iut/a/Dedans.java",
                "class Dedans {\n    void faire() {\n" + bloc + "\n        int x = 1;\n    }\n}\n")
        _verifie("4472 un bloc long DANS un corps est compte", len(m.suspects(racine)), 3)

        entete = "\n".join(f"// Ligne {i}." for i in range(m.SEUIL + 3))
        _ecrire(racine, "fr/univ_amu/iut/a/Dedans.java", entete + "\nclass Dehors {}\n")
        _verifie("4472 le meme bloc HORS d un corps ne coute rien", len(m.suspects(racine)), 0)
def test_4468_javadoc_non_relue() -> None:
    m = _charge("4468-javadoc-non-relue.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(racine, "fr/univ_amu/iut/a/Lu.java", "/// Le contrat de A.\nclass Lu {}\n")
        _ecrire(racine, "fr/univ_amu/iut/a/Jamais.java", "/// Le contrat de B.\nclass Jamais {}\n")

        # Sans manifeste, TOUT est suspect : c est le premier des deux faits que le cliquet tient.
        _verifie("4468 sans manifeste, aucun fichier n est blanchi", len(m.suspects(racine, {})), 2)

        # Inscrit avec l empreinte du jour, un fichier sort de la liste.
        lu = racine / "fr/univ_amu/iut/a/Lu.java"
        # L empreinte vient du COMPTEUR, que le garde emprunte plutot que de la redire : le temoin
        # emprunte la meme, sans quoi il eprouverait une troisieme definition.
        table = {"fr/univ_amu/iut/a/Lu.java": m.empreinte(lu)}
        _verifie("4468 un fichier inscrit sort de la liste", len(m.suspects(racine, table)), 1)

        # LE SECOND FAIT, celui qui distingue ce cliquet d une case cochee : la javadoc change, donc
        # elle n a pas ete relue SOUS SA FORME ACTUELLE, et le fichier redevient suspect.
        lu.write_text("/// Le contrat de A, reecrit en douce.\nclass Lu {}\n", encoding="utf-8")
        _verifie("4468 une javadoc reecrite en douce redevient suspecte", len(m.suspects(racine, table)), 2)
def test_4475_stage_non_dimensionne() -> None:
    m = _charge("4475-stage-non-dimensionne.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Le cas qui compte : la MEME classe, avec puis sans le geste qui fait suivre le stage. Le
        # garde accepte trois formes, et `sizeToScene` est celle que `FenetreDuBanc` prescrit ici :
        # un garde qui n accepterait que `setWidth` pousserait a figer le stage, donc au defaut.
        nu = ("class Nu {\n    @Start\n    void start(Stage fenetre) {\n"
              "        fenetre.setScene(new Scene(racine, 980, 980));\n    }\n}\n")
        _ecrire(racine, "fr/univ_amu/iut/a/NuTest.java", nu)
        _verifie("4475 une scene dimensionnee sans stage dimensionne est vue", len(m.suspects(racine)), 1)
        _ecrire(racine, "fr/univ_amu/iut/a/NuTest.java",
                nu.replace("    }\n}", "        fenetre.sizeToScene();\n    }\n}"))
        _verifie("4475 sizeToScene suffit, comme FenetreDuBanc le prescrit", len(m.suspects(racine)), 0)


def test_4476_javadoc_raconte_son_extraction() -> None:
    m = _charge("4476-javadoc-raconte-son-extraction.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Le cliquet ne vise pas toute mention d extraction, mais celle qui nomme l OUTIL qui l a
        # exigee : c est ce voisinage-la qui fait le recit, « on a scinde parce que PMD a rougi ».
        _ecrire(racine, "fr/univ_amu/iut/a/Recit.java",
                "/// Collaborateur extrait de ServiceImport (plafond NcssCount).\nclass Recit {}\n")
        _verifie("4476 un recit qui nomme l outil qui l a exige est vu", len(m.suspects(racine)), 1)
        # LA BORNE QUI COMPTE : le verbe et l outil dans DEUX phrases distinctes sont une mention
        # legitime. Un cliquet qui elargirait le voisinage au bloc entier les compterait, et sa liste
        # grossirait sans qu une javadoc ait change.
        _ecrire(racine, "fr/univ_amu/iut/a/Recit.java",
                "/// Ce que la classe garantit.\n///\n/// Voir ServiceImport pour le parcours complet.\nclass Recit {}\n")
        _verifie("4476 le nom de l outil seul, hors du verbe, est epargne", len(m.suspects(racine)), 0)


def test_4477_longueur_des_adr() -> None:
    """Le temoin d origine n affirmait que `isinstance(suspects(), list)`.

    Un garde qui aurait cesse de detecter le passait, ce qui est exactement le faux vert que cette
    suite existe pour interdire - et la phrase de cloture affirmait pourtant que les scripts
    detectent leur violation temoin. Trouve par mutation en passe 6 : neutraliser la detection ne
    faisait pas rougir la suite.

    Quatre fixtures pour quatre comportements, et un compte EXACT de 1. Ce que le garde EPARGNE est
    ce qui se casse : un en-tete OKF bavard ou un encart de revision long feraient rougir une ADR
    dont la DECISION tient en trois lignes, et c est la decision que le seuil borne.
    """
    m = _charge("4477-longueur-des-adr.py")
    entete = "---\ntype: adr\ntitle: \"Une decision\"\n---\n"
    long_ = " ".join(f"mot{i}" for i in range(m.SEUIL + 100))
    court = " ".join(f"mot{i}" for i in range(100))
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(racine, "0001-longue.md", entete + "# Titre\n\n" + long_ + "\n")
        _ecrire(racine, "0002-courte.md", entete + "# Titre\n\n" + court + "\n")
        # L encart de revision est retire AVANT la mesure : ses lignes sont indentees de quatre
        # espaces, et l admonition s arrete a la premiere ligne non indentee.
        encart = m.ENCART + "\n" + "\n".join("    " + l for l in long_.split(" "))
        _ecrire(racine, "0003-encart.md", entete + "# Titre\n\n" + encart + "\n\n" + court + "\n")
        # Le sommaire et le journal sont reserves : ils ne portent pas de decision.
        _ecrire(racine, "index.md", entete + "# Sommaire\n\n" + long_ + "\n")
        n = len(m.suspects(racine=racine))
        _verifie(
            "4477 compte l ADR longue, epargne la courte, l encart de revision et le sommaire",
            n,
            1,
        )


def test_rapport_et_resserrement() -> None:
    rapport = _charge("rapport.py")
    # Le parsing : une ligne normalisée doit être reconnue.
    ligne = "ADR 0099 | suspects=2 | cliquet=5 | verdict=a-resserrer"
    trouve = rapport.LIGNE_CLIQUET.search(ligne)
    _verifie("rapport.py parse une ligne de cliquet", bool(trouve), True)
    # La détection de resserrement : cliquet 5 pour 2 suspects -> ramener à 2.
    props = rapport.resserrements([("0099", 2, 5, "a-resserrer")])
    _verifie("rapport.py propose de resserrer 5 -> 2", props, [("0099", 2)])
    # Aucune proposition quand la marge colle.
    props2 = rapport.resserrements([("0099", 5, 5, "ok")])
    _verifie("rapport.py ne resserre pas une marge exacte", props2, [])

    # MESURER n est pas RESORBER, et le confondre a laisse trois chiffres perimes en une journee
    # (#4469). Une valeur qui MONTE ou qui ne bouge pas ne passe par aucun resserrement : la passe
    # d alignement doit malgre tout reposer les balises sur ce que l en-tete declare.
    resserre = _charge("resserre_cliquets.py")
    _verifie(
        "resserre_cliquets expose une passe d alignement, distincte du resserrement",
        callable(getattr(resserre, "aligner_les_balises", None)),
        True,
    )
    _verifie(
        "l alignement lit `ratchet:` comme `floor:` - les deux polarites vivent en balise",
        bool(resserre.SEUIL_DECLARE.search("floor: 7\n")) and bool(resserre.SEUIL_DECLARE.search("ratchet: 7\n")),
        True,
    )


def _completude(dossier: pathlib.Path | None = None, charges: set[str] | None = None) -> list[str]:
    """Les detecteurs que l'etape « Cliquets ADR » lance et que ce harnais n'exerce pas.

    L'etape lance `scripts/adr/[0-9]*.py`. Ce harnais existe pour prouver que chacun DETECTE
    encore : sans ce controle, un detecteur ajoute sans cas temoin n'est tenu que par son cliquet,
    c'est-a-dire par un compte qui ne monte pas - le trou exact que l'en-tete de ce fichier decrit.
    Mesure du 2026-08-23 : `2635-refus-sans-surface.py` et `3947-message-enveloppe.py` etaient dans
    ce cas, et rien ne pouvait le dire (#4268).
    """
    dossier = ICI if dossier is None else dossier
    charges = _charges if charges is None else charges
    detecteurs = {p.name for p in dossier.glob("[0-9]*.py")}
    if not detecteurs:
        raise SystemExit(
            f"Aucun detecteur sous {dossier} : c'est le HARNAIS qui est en cause, pas les scripts. "
            "Le dossier a-t-il ete deplace ?"
        )
    return sorted(detecteurs - charges)


def auto_test() -> int:
    """Teste le testeur.

    Ce fichier prouve que chaque detecteur detecte encore. Rien ne prouvait que LUI le fasse : un
    `_completude` qui aurait cesse de comparer rendrait une liste vide, c'est-a-dire un vert, sur un
    dossier ou un detecteur n'a aucun cas. C'est la forme meme du defaut que tout ce mecanisme
    combat, un cran plus haut (#4268).

    Repond a `--auto-test` comme les 30 autres gardes du depot, et pour la meme raison.

    ⚠️ Ce qu'il ne couvre PAS, et il vaut mieux le lire ici que le croire : il eprouve la
    FONCTION `_completude`, pas son cablage. Debrancher son appel plus bas rendrait le harnais
    vert sans que ces trois cas bronchent - mesure faite. Fermer ce trou demanderait de rendre le
    corps de `__main__` appelable avec un dossier injecte, ce qui ferait rejouer les quatorze cas
    reels a chaque essai. Le cout a paru superieur au risque : l'appel tient en une ligne, juste
    sous les cas, et se voit a la relecture.
    """
    echecs: list[str] = []

    def verifie(cas: str, obtenu, attendu) -> None:
        if obtenu == attendu:
            print(f"  ✔ {cas}")
        else:
            echecs.append(cas)
            print(f"  ✘ {cas} : attendu {attendu}, obtenu {obtenu}")

    print("Auto-test du harnais lui-même (#4268) :")
    with tempfile.TemporaryDirectory() as d:
        faux = pathlib.Path(d)
        (faux / "1234-temoin.py").write_text("", encoding="utf-8")
        # `rapport.py` ne commence pas par un chiffre : l'etape « Cliquets ADR » ne le lance pas,
        # le controle n'a donc pas a l'exiger. Sans ce cas, un controle trop large passerait.
        (faux / "rapport.py").write_text("", encoding="utf-8")

        verifie(
            "un détecteur sans cas témoin est signalé",
            _completude(faux, set()),
            ["1234-temoin.py"],
        )
        verifie(
            "le même, une fois exercé, ne l'est plus",
            _completude(faux, {"1234-temoin.py"}),
            [],
        )

    # Un dossier sans detecteur accuse le HARNAIS : il ne rend pas un vert rassurant. C'est le
    # patron des autres gardes du depot - distinguer « rien a redire » de « je n'ai rien lu ».
    with tempfile.TemporaryDirectory() as d:
        try:
            _completude(pathlib.Path(d), set())
            accuse = False
        except SystemExit:
            accuse = True
    verifie("un dossier sans détecteur accuse le harnais, au lieu de rendre un vert", accuse, True)

    print("\n3 cas, dont 1 qui DOIT rougir sur un harnais aveugle.")
    if echecs:
        print(f"{len(echecs)} cas en échec : ne pas se fier au verdict de ce harnais.", file=sys.stderr)
        return 1
    print("Auto-test concluant.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        raise SystemExit(auto_test())
    print("Auto-test des scripts de vérification ADR (#2467) :")
    for essai in (
        test_0008_echec_silencieux,
        test_3053_capture_libelle,
        test_0010_dialogue_hors_port,
        test_0035_pictogramme,
        test_0037_slot_actions,
        test_2843_tiret_cadratin,
        test_2843_prose_documentation,
        test_2843_zone_vide_est_une_erreur,
        test_2843_couverture_distingue_dedans_dehors,
        test_2843_balayage_non_recursif,
        test_2493_modale_suit_croissance,
        test_2635_refus_sans_surface,
        test_3947_message_enveloppe,
        test_4359_javadoc_narratif,
        test_4366_avertissement_en_pictogramme,
        test_4366_pictogramme_en_tete_de_ligne,
        test_4368_apostrophe_en_libelle,
        test_4395_renvois_en_javadoc,
        test_4359_blocs_relus,
        test_loupe_4359_javadoc_vieillie,
        test_loupe_0020,
        test_loupe_0044,
        test_4472_commentaire_en_corps,
        test_4468_javadoc_non_relue,
        test_4475_stage_non_dimensionne,
        test_4476_javadoc_raconte_son_extraction,
        test_4477_longueur_des_adr,
        test_rapport_et_resserrement,
    ):
        essai()
    decouverts = _completude()
    if decouverts:
        print(
            f"\n{len(decouverts)} détecteur(s) sans cas témoin : " + ", ".join(decouverts),
            file=sys.stderr,
        )
        print(
            "   Ils ne sont tenus que par leur cliquet, c'est-à-dire par un compte qui ne monte pas.\n"
            "   C'est le trou que ce harnais existe pour fermer.",
            file=sys.stderr,
        )
    if _echecs:
        print(f"\n{len(_echecs)} cas en échec : un script ne détecte plus ce qu'il devrait.", file=sys.stderr)
    if _echecs or decouverts:
        sys.exit(1)
    print(
        f"\nLes {len(_charges)} scripts chargés détectent leur violation témoin et ignorent les "
        "commentaires ;\naucun détecteur lancé par « Cliquets ADR » n'est sans cas."
    )
