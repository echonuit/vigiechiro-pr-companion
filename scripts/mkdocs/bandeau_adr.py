"""Redessine, sur le site, le bandeau que la conversion OKF a fait passer en en-tete (chantier A).

Avant la conversion, le statut, le chantier et la verification d une ADR etaient trois PUCES, donc
visibles. Passees en frontmatter, elles deviennent lisibles par la machine et invisibles pour
l humain : MkDocs les retire du corps rendu. Ce serait un recul, c est a dire l inverse de ce que le
chantier cherche. Ce hook les remet sous le titre, depuis la meme et unique source.

**Il emet du HTML, pas du markdown, et ce n est pas un gout.** Le spike a monte la mutation : en
emettant une admonition `!!! info`, le bandeau depend de l extension `admonition`. Retiree, elle
laisse la syntaxe brute visible dans la page. Un bandeau qui se degrade en texte apparent selon la
configuration du site n est pas un bandeau, c est une panne discrete.

**Il n emet aucun cadratin, pas meme en entite.** Un `&mdash;` traverserait le cliquet du tiret
cadratin, qui cherche le glyphe, et afficherait pourtant le glyphe au lecteur. Un garde qu on
contourne par l encodage ne garde plus rien.

**L enonce de l article vient de `CONSTITUTION.md`**, lu une fois a la construction. Un code seul
(`A18`) ne dit rien a qui ouvre une ADR, et recopier l enonce dans chaque en-tete le ferait deriver.
Un article cite par une ADR et absent de la constitution ARRETE la construction : c est un
rattachement casse, et le site ne doit pas le rendre en silence.
"""

import html
import sys
import pathlib
import re

NIVEAUX = {
    "certaine": ("Vérification certaine", "un test ou un script déterministe la tient"),
    "probable": ("Vérification probable", "un script de suspects et son cliquet la tiennent"),
    "humaine": ("Vérification humaine", "non mécanisée, et le motif est déclaré"),
}

ARTICLE = re.compile(r"^###\s+(A\d+)\s*:\s*(.+?)\s*$", re.M)

_articles: dict[str, str] = {}


def lit_articles(chemin: pathlib.Path) -> dict[str, str]:
    """Les articles de la constitution, code vers enonce."""
    return dict(ARTICLE.findall(chemin.read_text(encoding="utf-8")))


def on_config(config):
    global _articles
    racine = pathlib.Path(config["config_file_path"]).resolve().parent
    _articles = lit_articles(racine / "CONSTITUTION.md")
    return config


def _code(valeur: str) -> str:
    """Echappe, puis rend les chevrons de code en `<code>`.

    Le champ `chantier` cite souvent un fichier ou un fragment entre chevrons. Echappe sans plus, le
    lecteur voit les chevrons eux-memes : le bandeau afficherait sa propre syntaxe.
    """
    morceaux = html.escape(valeur).split("`")
    return (
        "".join(m if i % 2 == 0 else f"<code>{m}</code>" for i, m in enumerate(morceaux))
        if len(morceaux) % 2
        else html.escape(valeur)
    )


def _ligne(intitule: str, valeur: str) -> str:
    return (
        '<div class="adr-bandeau__ligne">'
        f'<span class="adr-bandeau__cle">{html.escape(intitule)}</span>'
        f'<span class="adr-bandeau__valeur">{valeur}</span>'
        "</div>"
    )


def bandeau(meta: dict, articles: dict[str, str] | None = None) -> str:
    """Le bandeau HTML d une ADR, depuis ses seules metadonnees."""
    articles = _articles if articles is None else articles
    lignes = []

    statut = str(meta.get("status", "")).strip()
    date = str(meta.get("decided_at", "")).strip()
    if statut:
        libelle = "En vigueur" if statut == "stable" else "Dépassée"
        if date:
            libelle += f", {date}"
        lignes.append(_ligne("Statut", html.escape(libelle)))

    code = str(meta.get("article", "")).strip()
    if code:
        if code not in articles:
            raise ValueError(
                f"l'ADR « {meta.get('title', '?')} » se rattache à l'article {code}, "
                "que CONSTITUTION.md ne déclare pas"
            )
        lignes.append(_ligne("Article", f"{html.escape(code)} · {html.escape(articles[code])}"))

    chantier = str(meta.get("chantier", "")).strip()
    if chantier:
        lignes.append(_ligne("Chantier", _code(chantier)))

    niveau = str(meta.get("verification", "")).strip()
    if niveau:
        titre, glose = NIVEAUX.get(niveau, (f"Vérification {niveau}", ""))
        tenu = meta.get("enforced_by") or []
        if tenu:
            glose = ", ".join(f"<code>{html.escape(str(t))}</code>" for t in tenu)
        else:
            # Avant la conversion, la loupe etait VISIBLE dans la puce : « humaine - <motif>
            # Loupe : `script` ». Le convertisseur l a gardee dans la PROSE de la note pour les ADR
            # reprises ; une ADR ecrite a la main declare un champ `loupe:` que la note ne nomme pas
            # forcement. Les deux se rendent, sans quoi le bandeau serait un recul pour celles-la.
            morceaux = []
            if meta.get("verification_note"):
                morceaux.append(html.escape(str(meta["verification_note"])))
            if meta.get("loupe"):
                morceaux.append(
                    "Loupe : "
                    + ", ".join(f"<code>{html.escape(str(l))}</code>" for l in meta["loupe"])
                )
            if morceaux:
                glose = " ".join(morceaux)
        lignes.append(_ligne(titre, glose))

    if not lignes:
        return ""
    return '<div class="adr-bandeau">' + "".join(lignes) + "</div>"


def on_page_markdown(markdown, page, config, files):  # noqa: ARG001  (signature MkDocs)
    if str(page.meta.get("type", "")) != "adr":
        return markdown
    rendu = bandeau(page.meta)
    if not rendu:
        return markdown
    # Le bandeau se pose APRES le titre de niveau 1 : une page qui commencerait par un bloc HTML
    # perdrait son titre dans le sommaire du theme.
    lignes = markdown.splitlines()
    for i, ligne in enumerate(lignes):
        if ligne.startswith("# "):
            return "\n".join(lignes[: i + 1] + ["", rendu, ""] + lignes[i + 1 :])
    return rendu + "\n\n" + markdown


def _auto_test() -> int:
    """Eprouve le bandeau sur des metadonnees au verdict connu.

    Ce hook est le seul dispositif du chantier dont l absence de couverture serait MUETTE : s il
    cessait de reconnaitre l en-tete, il rendrait le markdown inchange, 199 pages sortiraient sans
    bandeau, et `mkdocs build --strict` ne dirait rien. Un garde qui cesse de detecter ne rougit pas.
    """
    import types

    ARTICLES = {
        "A18": "L'utilisateur possède ses fichiers",
        "A28": "Un avertissement se dit en mots",
    }
    echecs = []

    def verifie(titre: str, ok: bool, detail: str = "") -> None:
        print(f"  {'✔' if ok else '✘'} {titre}{'' if ok else '  -> ' + detail}")
        if not ok:
            echecs.append(titre)

    complet = {
        "type": "adr",
        "title": "Témoin",
        "status": "stable",
        "article": "A18",
        "decided_at": "2026-08-24",
        "chantier": "#1234",
        "verification": "certaine",
        "enforced_by": ["TemoinTest#cas"],
    }
    rendu = bandeau(complet, ARTICLES)
    verifie(
        "le bandeau porte les quatre lignes",
        all(m in rendu for m in ("Statut", "Article", "Chantier", "Vérification certaine")),
        rendu[:90],
    )
    # L enonce est ECHAPPE : l apostrophe sort en `&#x27;`. L asserter sous sa forme brute passerait
    # a cote de l echappement, qui est ce qui empeche un titre d ADR de casser le HTML du bandeau.
    verifie(
        "l enonce de l article vient de la constitution, echappe",
        html.escape("L'utilisateur possède ses fichiers") in rendu,
        rendu[:90],
    )
    verifie("l applicateur est rendu en code", "<code>TemoinTest#cas</code>" in rendu, rendu[:90])

    # C est le HTML qui est exige, pas une admonition : une admonition se degrade en texte apparent
    # quand l extension manque, ce qui est une panne discrete.
    verifie(
        "le bandeau est du HTML, pas une admonition",
        rendu.startswith('<div class="adr-bandeau">') and "!!!" not in rendu,
        rendu[:60],
    )

    # Aucun cadratin, pas meme en entite : le cliquet cherche le glyphe et manquerait `&mdash;`.
    verifie(
        "aucun cadratin, ni glyphe ni entite",
        chr(0x2014) not in rendu and "&mdash;" not in rendu and "&#8212;" not in rendu,
    )

    humaine = dict(
        complet,
        verification="humaine",
        enforced_by=None,
        loupe=["scripts/adr/2843-tiret-cadratin.py"],
    )
    rendu_h = bandeau(humaine, ARTICLES)
    verifie(
        "une verification humaine montre sa loupe",
        "Loupe :" in rendu_h and "2843-tiret-cadratin.py" in rendu_h,
        rendu_h[:90],
    )
    # Une ADR ecrite a la main porte souvent les DEUX : un motif, et le champ `loupe:`. Rendre l un
    # OU l autre cacherait les scripts a celles dont la note ne les nomme pas, ce qui etait le cas.
    avec_note = dict(humaine, verification_note="aucun motif textuel ne tranche")
    rendu_n = bandeau(avec_note, ARTICLES)
    verifie(
        "le motif ET la loupe se rendent ensemble",
        "aucun motif textuel ne tranche" in rendu_n and "2843-tiret-cadratin.py" in rendu_n,
        rendu_n[:120],
    )

    # Un article que la constitution ne declare pas ARRETE la construction : c est un rattachement
    # casse, et le site ne doit pas le rendre en silence.
    try:
        bandeau(dict(complet, article="A99"), ARTICLES)
        verifie("un article inconnu arrête la construction", False, "aucune levée")
    except ValueError:
        verifie("un article inconnu arrête la construction", True)

    verifie("des metadonnees vides ne fabriquent aucun bandeau", bandeau({}, ARTICLES) == "")

    # Le placement : sous le titre de niveau 1, sinon le theme perd le titre de son sommaire.
    # `on_page_markdown` lit le vocabulaire global, que `on_config` remplit a la construction : on
    # le pose ici comme MkDocs le poserait, sinon le cas eprouverait l absence de configuration.
    global _articles
    _articles = ARTICLES
    page = types.SimpleNamespace(meta=complet)
    sortie = on_page_markdown("# Témoin\n\n## Contexte\n", page, None, None)
    lignes = sortie.split("\n")
    verifie(
        "le bandeau se pose SOUS le titre de niveau 1",
        lignes[0].startswith("# ") and any("adr-bandeau" in l for l in lignes[1:4]),
        sortie[:80],
    )

    # Une page qui n est pas une ADR ne doit rien recevoir.
    autre = types.SimpleNamespace(meta={"type": "page"})
    verifie(
        "une page qui n est pas une ADR est rendue inchangée",
        on_page_markdown("# Autre\n", autre, None, None) == "# Autre\n",
    )

    print()
    if echecs:
        print(
            f"ÉCHEC : {len(echecs)} cas. Le bandeau ne fait pas ce qu il annonce.", file=sys.stderr
        )
        return 1
    print("Auto-test concluant : le bandeau rend ce qu il promet, et refuse un rattachement cassé.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    raise SystemExit("Ce fichier est un hook MkDocs. Lancez-le avec --auto-test pour l'éprouver.")
