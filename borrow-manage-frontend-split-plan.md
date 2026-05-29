# BorrowManage 复杂页面拆分示例

这份文档以借书页面 `BorrowManage` 为例，说明一个复杂前端页面可以如何拆分。目标不是把文件拆得越多越好，而是让页面结构、业务区域和局部实现都能清楚地被看到。

## 页面目标

当前 `BorrowManage` 可以从简单的“输入图书编号并登记借书”升级成一个“借书工作台”。页面可以包含这些复杂功能：

1. 读者面板：搜索读者，展示读者信息、当前借阅数、逾期状态和是否允许继续借书。
2. 图书选择面板：按书名、作者、分类、ISBN 搜索图书，展示候选图书、库存状态和是否可借。
3. 借阅草稿区：把一本或多本书加入待借清单，设置应还日期，删除草稿项。
4. 借阅规则检查：展示库存不足、读者逾期、超过最大借阅数量、图书不可借等检查结果。
5. 借书确认区：汇总读者、图书、应还日期和规则状态，通过检查后提交借书。
6. 当前借阅记录区：展示借阅记录，支持筛选、查看详情和办理归还。

## 核心拆分原则

`index.tsx` 应该显式展示页面的整体结构，不要把主要页面布局藏到 `BorrowManageLayout` 这种组件里。这样读代码的人能直接看到页面由哪些业务区域组成。

简单组件直接写成一个 `.tsx` 文件。只有当一个组件内部确实包含多个子组件、自己的 hooks、objects 或 functions 时，才为它单独开文件夹。

页面级 `hooks/` 存放多个区域共享的页面状态，例如当前读者、借阅草稿、借阅记录列表。

页面级 `objects/` 存放当前页面共同使用的对象，例如 `BorrowDraft`、`BorrowRuleCheck`、`BorrowManageSummary`。

页面级 `functions/` 存放当前页面共享的纯函数，例如规则检查、摘要计算、记录过滤。

复杂组件内部也可以继续递归拆分自己的 `hooks/`、`objects/`、`functions/` 和子组件。但如果某一层只有一个组件文件，就直接放在当前层，不要为了形式再包一层目录。

## 推荐目录结构

```text
BorrowManage/
  index.tsx

  hooks/
    useBorrowManagePage.ts
    useBorrowDraft.ts
    useBorrowRecords.ts

  objects/
    BorrowDraft.ts
    BorrowDraftItem.ts
    BorrowRuleCheck.ts
    BorrowManageSummary.ts

  functions/
    buildBorrowRuleChecks.ts
    buildBorrowSummary.ts

  components/
    BorrowManageHeader.tsx
    BorrowRulePanel.tsx
    BorrowConfirmPanel.tsx

    ReaderPanel/
      index.tsx
      ReaderSearchBox.tsx
      ReaderProfileCard.tsx
      ReaderBorrowStats.tsx
      hooks/
        useReaderPanel.ts
      objects/
        ReaderEligibility.ts

    BookSelectionPanel/
      index.tsx
      BookSearchToolbar.tsx
      hooks/
        useBookSelectionPanel.ts
      objects/
        BookSearchFilter.ts
        BookCandidateView.ts
      components/
        BookCandidateList/
          index.tsx
          BookCandidateRow.tsx
          InventoryBadge.tsx
          AddToDraftButton.tsx

    BorrowDraftPanel/
      index.tsx
      BorrowDraftList.tsx
      BorrowDraftItemRow.tsx
      DueDateSelector.tsx
      RemoveDraftItemButton.tsx

    BorrowRecordPanel/
      index.tsx
      BorrowRecordToolbar.tsx
      hooks/
        useBorrowRecordPanel.ts
      objects/
        BorrowRecordFilter.ts
      components/
        BorrowRecordTable/
          index.tsx
          BorrowRecordRow.tsx
          BorrowRecordStatusBadge.tsx
          BorrowRecordActions.tsx
```

## 三层结构说明

第一层是页面层：

```text
BorrowManage/
  index.tsx
  hooks/
  objects/
  functions/
  components/
```

这一层负责表达“这个页面是什么”。`index.tsx` 只保留页面骨架和业务区域组合。

第二层是业务区域层：

```text
components/
  ReaderPanel/
  BookSelectionPanel/
  BorrowDraftPanel/
  BorrowRecordPanel/
  BorrowRulePanel.tsx
  BorrowConfirmPanel.tsx
```

这一层负责表达“页面由哪些业务区域组成”。如果业务区域简单，就直接是一个 `.tsx` 文件；如果业务区域复杂，就开文件夹。

第三层是复杂区域内部模块层：

```text
BookSelectionPanel/
  index.tsx
  BookSearchToolbar.tsx
  hooks/
  objects/
  components/
    BookCandidateList/
```

这一层负责表达“某个复杂业务区域内部如何继续拆分”。例如 `BookSelectionPanel` 里面有搜索条件、候选列表、库存状态、加入草稿按钮，所以可以继续拆。

## `index.tsx` 示例

```tsx
import { BookSelectionPanel } from './components/BookSelectionPanel'
import { BorrowConfirmPanel } from './components/BorrowConfirmPanel'
import { BorrowDraftPanel } from './components/BorrowDraftPanel'
import { BorrowManageHeader } from './components/BorrowManageHeader'
import { BorrowRecordPanel } from './components/BorrowRecordPanel'
import { BorrowRulePanel } from './components/BorrowRulePanel'
import { ReaderPanel } from './components/ReaderPanel'
import { useBorrowManagePage } from './hooks/useBorrowManagePage'

export default function BorrowManage() {
  const page = useBorrowManagePage()

  return (
    <main className="min-h-screen bg-slate-50">
      <BorrowManageHeader summary={page.summary} />

      <section className="grid gap-6 px-6 py-6 lg:grid-cols-[360px_1fr]">
        <div className="space-y-6">
          <ReaderPanel reader={page.reader} />
          <BorrowRulePanel checks={page.ruleChecks} />
        </div>

        <div className="space-y-6">
          <BookSelectionPanel books={page.books} draft={page.draft} />
          <BorrowDraftPanel draft={page.draft} />
          <BorrowConfirmPanel confirmation={page.confirmation} />
          <BorrowRecordPanel records={page.records} />
        </div>
      </section>
    </main>
  )
}
```

这个 `index.tsx` 的重点是：读者能直接看到页面分成左侧读者/规则区域，右侧选书/草稿/确认/记录区域。至于每个区域内部怎么实现，再进入对应组件目录查看。

## 什么时候不要继续拆

如果 `BorrowRulePanel` 只是渲染一个规则检查列表，代码不长，就直接写成：

```text
components/
  BorrowRulePanel.tsx
```

不要写成：

```text
components/
  BorrowRulePanel/
    index.tsx
    components/
      BorrowRuleCheckList.tsx
```

后一种结构只有形式上的模块化，会增加查找成本。

如果 `BookSearchToolbar` 只是关键词输入、分类选择、库存开关三个控件组合，也可以直接放在：

```text
BookSelectionPanel/
  BookSearchToolbar.tsx
```

不用继续拆成：

```text
BookSearchToolbar/
  index.tsx
  KeywordInput.tsx
  CategorySelect.tsx
  AvailabilityToggle.tsx
```

只有当 `KeywordInput` 自己有复杂交互、校验、联想搜索，或者多个地方复用时，才值得单独拆出来。

## 判断标准

可以用下面几个问题决定是否开文件夹：

1. 这个组件是否超过一个清晰的业务职责？
2. 它是否需要自己的 hooks、objects 或 functions？
3. 它内部是否有多个子模块需要协作？
4. 它的子组件是否需要被单独阅读、测试或复用？
5. 如果不开目录，这个文件是否会明显过长、难以定位？

如果大部分答案是“否”，就不要继续拆。直接一个 `.tsx` 文件通常更清楚。

