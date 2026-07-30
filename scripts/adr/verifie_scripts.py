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

# Nomme parce qu il porte TROIS cas, la ou ses voisins n en ont qu un : le cliquet Java, la tolerance
# zero d une zone nettoyee, et le refus d une zone qui ne balaie aucun fichier.
ADR_2843 = "2843-tiret-cadratin.py"

_echecs: list[str] = []


def _charge(nom: str):
    """Importe un script au nom non-importable (chiffres, tirets) par son chemin."""
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
            "Exemple.java",
            "class E {\n"
            "  void a() { try { x(); } catch (Exception e) { } }\n"  # vide -> compte
            "  void b() { try { x(); } catch (Exception e) { log(e); } }\n"  # tracé -> non
            "  void c() { try { x(); } catch (Exception e) { /* ignoré */ } }\n"  # commentaire seul -> compte
            "  /* void mort() { try{}catch(E e){} } */\n"  # catch EN commentaire -> non
            "}\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("0008 détecte les catch vides (vide + commentaire-seul)", n, 2)


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
            "}\n",
        )
        n = len(m.suspects(racine))
        # Contrairement a ses voisins, ce script COMPTE les commentaires : la regle porte sur « la doc
        # et les commentaires », qui sont ici la matiere et non le bruit. Les trois formes CITEES, en
        # revanche, ne sont pas de la prose (meme regle que les zones Markdown) : le glyphe defini en
        # litteral, entre chevrons de code, ou dans un libelle recopie. Ce cas garde les deux sens a la
        # fois - si le motif de citation devenait gourmand il avalerait la prose et n tomberait a 0 ;
        # s il cessait de proteger, n monterait a 4 et le cliquet buterait sur un plancher fantome.
        _verifie("2843 compte la prose Java, epargne les trois formes citees", n, 1)


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
            "Une phrase saine : deux-points.\n",  # rien -> non
        )
        n = len(m.prose(racine))
        # La regle « ce qui est cite n est pas de la prose » couvre le glyphe ET les libelles de
        # l application. Si le motif de citation devenait trop gourmand, il avalerait la prose et ce
        # cas tomberait a zero : c est exactement la deflation que ce fichier existe pour interdire.
        _verifie("2843 zone nettoyee : compte la prose, epargne les citations", n, 1)


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


if __name__ == "__main__":
    print("Auto-test des scripts de vérification ADR (#2467) :")
    for essai in (
        test_0008_echec_silencieux,
        test_0010_dialogue_hors_port,
        test_0035_pictogramme,
        test_0037_slot_actions,
        test_2843_tiret_cadratin,
        test_2843_prose_documentation,
        test_2843_zone_vide_est_une_erreur,
        test_2493_modale_suit_croissance,
        test_loupe_0020,
        test_loupe_0044,
        test_rapport_et_resserrement,
    ):
        essai()
    if _echecs:
        print(f"\n{len(_echecs)} cas en échec : un script ne détecte plus ce qu'il devrait.", file=sys.stderr)
        sys.exit(1)
    print("\nTous les scripts détectent leur violation témoin et ignorent les commentaires.")
