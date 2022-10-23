package com.yandex.daggerlite.testing.doc_testing

import org.jetbrains.dokka.model.doc.*

abstract class DocTagTransformAdapter {
    open fun transformA(docTag: A): DocTag = docTag
    open fun transformBig(docTag: Big): DocTag = docTag
    open fun transformB(docTag: B): DocTag = docTag
    open fun transformBlockQuote(docTag: BlockQuote): DocTag = docTag
    open fun transformBr(docTag: Br): DocTag = docTag
    open fun transformCite(docTag: Cite): DocTag = docTag
    open fun transformCodeInline(docTag: CodeInline): DocTag = docTag
    open fun transformCodeBlock(docTag: CodeBlock): DocTag = docTag
    open fun transformCustomDocTag(docTag: CustomDocTag): DocTag = docTag
    open fun transformDd(docTag: Dd): DocTag = docTag
    open fun transformDfn(docTag: Dfn): DocTag = docTag
    open fun transformDir(docTag: Dir): DocTag = docTag
    open fun transformDiv(docTag: Div): DocTag = docTag
    open fun transformDl(docTag: Dl): DocTag = docTag
    open fun transformDocumentationLink(docTag: DocumentationLink): DocTag = docTag
    open fun transformDt(docTag: Dt): DocTag = docTag
    open fun transformEm(docTag: Em): DocTag = docTag
    open fun transformFont(docTag: Font): DocTag = docTag
    open fun transformFooter(docTag: Footer): DocTag = docTag
    open fun transformFrame(docTag: Frame): DocTag = docTag
    open fun transformFrameSet(docTag: FrameSet): DocTag = docTag
    open fun transformH1(docTag: H1): DocTag = docTag
    open fun transformH2(docTag: H2): DocTag = docTag
    open fun transformH3(docTag: H3): DocTag = docTag
    open fun transformH4(docTag: H4): DocTag = docTag
    open fun transformH5(docTag: H5): DocTag = docTag
    open fun transformH6(docTag: H6): DocTag = docTag
    open fun transformHead(docTag: Head): DocTag = docTag
    open fun transformHeader(docTag: Header): DocTag = docTag
    open fun transformHorizontalRule(docTag: HorizontalRule): DocTag = docTag
    open fun transformHtml(docTag: Html): DocTag = docTag
    open fun transformI(docTag: I): DocTag = docTag
    open fun transformIFrame(docTag: IFrame): DocTag = docTag
    open fun transformImg(docTag: Img): DocTag = docTag
    open fun transformIndex(docTag: Index): DocTag = docTag
    open fun transformInput(docTag: Input): DocTag = docTag
    open fun transformLi(docTag: Li): DocTag = docTag
    open fun transformLink(docTag: Link): DocTag = docTag
    open fun transformListing(docTag: Listing): DocTag = docTag
    open fun transformMain(docTag: Main): DocTag = docTag
    open fun transformMenu(docTag: Menu): DocTag = docTag
    open fun transformMeta(docTag: Meta): DocTag = docTag
    open fun transformNav(docTag: Nav): DocTag = docTag
    open fun transformNoFrames(docTag: NoFrames): DocTag = docTag
    open fun transformNoScript(docTag: NoScript): DocTag = docTag
    open fun transformOl(docTag: Ol): DocTag = docTag
    open fun transformP(docTag: P): DocTag = docTag
    open fun transformPre(docTag: Pre): DocTag = docTag
    open fun transformScript(docTag: Script): DocTag = docTag
    open fun transformSection(docTag: Section): DocTag = docTag
    open fun transformSmall(docTag: Small): DocTag = docTag
    open fun transformSpan(docTag: Span): DocTag = docTag
    open fun transformStrikethrough(docTag: Strikethrough): DocTag = docTag
    open fun transformStrong(docTag: Strong): DocTag = docTag
    open fun transformSub(docTag: Sub): DocTag = docTag
    open fun transformSup(docTag: Sup): DocTag = docTag
    open fun transformTable(docTag: Table): DocTag = docTag
    open fun transformText(docTag: Text): DocTag = docTag
    open fun transformTBody(docTag: TBody): DocTag = docTag
    open fun transformTd(docTag: Td): DocTag = docTag
    open fun transformTFoot(docTag: TFoot): DocTag = docTag
    open fun transformTh(docTag: Th): DocTag = docTag
    open fun transformTHead(docTag: THead): DocTag = docTag
    open fun transformTitle(docTag: Title): DocTag = docTag
    open fun transformTr(docTag: Tr): DocTag = docTag
    open fun transformTt(docTag: Tt): DocTag = docTag
    open fun transformU(docTag: U): DocTag = docTag
    open fun transformUl(docTag: Ul): DocTag = docTag
    open fun transformVar(docTag: Var): DocTag = docTag
    open fun transformCaption(docTag: Caption): DocTag = docTag

    fun transform(docTag: DocTag): DocTag = when (docTag) {
        is A -> transformA(docTag.copy(children = docTag.children.map(::transform)))
        is Big -> transformBig(docTag.copy(children = docTag.children.map(::transform)))
        is B -> transformB(docTag.copy(children = docTag.children.map(::transform)))
        is BlockQuote -> transformBlockQuote(docTag.copy(children = docTag.children.map(::transform)))
        Br -> transformBr(Br)
        is Cite -> transformCite(docTag.copy(children = docTag.children.map(::transform)))
        is CodeInline -> transformCodeInline(docTag.copy(children = docTag.children.map(::transform)))
        is CodeBlock -> transformCodeBlock(docTag.copy(children = docTag.children.map(::transform)))
        is CustomDocTag -> transformCustomDocTag(docTag.copy(children = docTag.children.map(::transform)))
        is Dd -> transformDd(docTag.copy(children = docTag.children.map(::transform)))
        is Dfn -> transformDfn(docTag.copy(children = docTag.children.map(::transform)))
        is Dir -> transformDir(docTag.copy(children = docTag.children.map(::transform)))
        is Div -> transformDiv(docTag.copy(children = docTag.children.map(::transform)))
        is Dl -> transformDl(docTag.copy(children = docTag.children.map(::transform)))
        is DocumentationLink -> transformDocumentationLink(docTag.copy(children = docTag.children.map(::transform)))
        is Dt -> transformDt(docTag.copy(children = docTag.children.map(::transform)))
        is Em -> transformEm(docTag.copy(children = docTag.children.map(::transform)))
        is Font -> transformFont(docTag.copy(children = docTag.children.map(::transform)))
        is Footer -> transformFooter(docTag.copy(children = docTag.children.map(::transform)))
        is Frame -> transformFrame(docTag.copy(children = docTag.children.map(::transform)))
        is FrameSet -> transformFrameSet(docTag.copy(children = docTag.children.map(::transform)))
        is H1 -> transformH1(docTag.copy(children = docTag.children.map(::transform)))
        is H2 -> transformH2(docTag.copy(children = docTag.children.map(::transform)))
        is H3 -> transformH3(docTag.copy(children = docTag.children.map(::transform)))
        is H4 -> transformH4(docTag.copy(children = docTag.children.map(::transform)))
        is H5 -> transformH5(docTag.copy(children = docTag.children.map(::transform)))
        is H6 -> transformH6(docTag.copy(children = docTag.children.map(::transform)))
        is Head -> transformHead(docTag.copy(children = docTag.children.map(::transform)))
        is Header -> transformHeader(docTag.copy(children = docTag.children.map(::transform)))
        HorizontalRule -> transformHorizontalRule(HorizontalRule)
        is Html -> transformHtml(docTag.copy(children = docTag.children.map(::transform)))
        is I -> transformI(docTag.copy(children = docTag.children.map(::transform)))
        is IFrame -> transformIFrame(docTag.copy(children = docTag.children.map(::transform)))
        is Img -> transformImg(docTag.copy(children = docTag.children.map(::transform)))
        is Index -> transformIndex(docTag.copy(children = docTag.children.map(::transform)))
        is Input -> transformInput(docTag.copy(children = docTag.children.map(::transform)))
        is Li -> transformLi(docTag.copy(children = docTag.children.map(::transform)))
        is Link -> transformLink(docTag.copy(children = docTag.children.map(::transform)))
        is Listing -> transformListing(docTag.copy(children = docTag.children.map(::transform)))
        is Main -> transformMain(docTag.copy(children = docTag.children.map(::transform)))
        is Menu -> transformMenu(docTag.copy(children = docTag.children.map(::transform)))
        is Meta -> transformMeta(docTag.copy(children = docTag.children.map(::transform)))
        is Nav -> transformNav(docTag.copy(children = docTag.children.map(::transform)))
        is NoFrames -> transformNoFrames(docTag.copy(children = docTag.children.map(::transform)))
        is NoScript -> transformNoScript(docTag.copy(children = docTag.children.map(::transform)))
        is Ol -> transformOl(docTag.copy(children = docTag.children.map(::transform)))
        is P -> transformP(docTag.copy(children = docTag.children.map(::transform)))
        is Pre -> transformPre(docTag.copy(children = docTag.children.map(::transform)))
        is Script -> transformScript(docTag.copy(children = docTag.children.map(::transform)))
        is Section -> transformSection(docTag.copy(children = docTag.children.map(::transform)))
        is Small -> transformSmall(docTag.copy(children = docTag.children.map(::transform)))
        is Span -> transformSpan(docTag.copy(children = docTag.children.map(::transform)))
        is Strikethrough -> transformStrikethrough(docTag.copy(children = docTag.children.map(::transform)))
        is Strong -> transformStrong(docTag.copy(children = docTag.children.map(::transform)))
        is Sub -> transformSub(docTag.copy(children = docTag.children.map(::transform)))
        is Sup -> transformSup(docTag.copy(children = docTag.children.map(::transform)))
        is Table -> transformTable(docTag.copy(children = docTag.children.map(::transform)))
        is Text -> transformText(docTag.copy(children = docTag.children.map(::transform)))
        is TBody -> transformTBody(docTag.copy(children = docTag.children.map(::transform)))
        is Td -> transformTd(docTag.copy(children = docTag.children.map(::transform)))
        is TFoot -> transformTFoot(docTag.copy(children = docTag.children.map(::transform)))
        is Th -> transformTh(docTag.copy(children = docTag.children.map(::transform)))
        is THead -> transformTHead(docTag.copy(children = docTag.children.map(::transform)))
        is Title -> transformTitle(docTag.copy(children = docTag.children.map(::transform)))
        is Tr -> transformTr(docTag.copy(children = docTag.children.map(::transform)))
        is Tt -> transformTt(docTag.copy(children = docTag.children.map(::transform)))
        is U -> transformU(docTag.copy(children = docTag.children.map(::transform)))
        is Ul -> transformUl(docTag.copy(children = docTag.children.map(::transform)))
        is Var -> transformVar(docTag.copy(children = docTag.children.map(::transform)))
        is Caption -> transformCaption(docTag.copy(children = docTag.children.map(::transform)))
    }
}