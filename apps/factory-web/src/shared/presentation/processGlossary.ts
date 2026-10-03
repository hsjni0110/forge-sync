export const PROCESS_GLOSSARY = {
  REPLAY: {
    label: "과거 데이터 재생",
    explanation: "기록된 관찰을 원래 순서에 따라 다시 보여줍니다.",
    contractTerm: "Replay",
  },
  REPLAY_CURSOR: {
    label: "재생 위치",
    explanation: "모든 화면이 함께 가리키는 재생 시점입니다.",
    contractTerm: "Replay Cursor",
  },
  TWIN_VERSION: {
    label: "트윈 버전",
    explanation: "설비 상태가 갱신된 순서입니다.",
    contractTerm: "Twin Version",
  },
  FRESHNESS: {
    label: "데이터 최신성",
    explanation: "ForgeSync에 마지막으로 반영된 뒤 지난 시간을 나타냅니다.",
    contractTerm: "Freshness",
  },
  PROVENANCE: {
    label: "출처와 계보",
    explanation: "값의 원천과 변환 과정을 보여줍니다.",
    contractTerm: "Provenance",
  },
  OBSERVED: {
    label: "관찰에서 확인",
    explanation: "원천 데이터에서 직접 확인하거나 재구성한 내용입니다.",
    contractTerm: "Observed",
  },
  SIMULATED: {
    label: "작성된 표현",
    explanation: "실제 측량이나 관찰이 아닌 화면 구성을 위한 표현입니다.",
    contractTerm: "Simulated",
  },
  NOT_EVALUATED: {
    label: "평가할 수 없음",
    explanation: "현재 근거로는 값을 계산하거나 판정할 수 없습니다.",
    contractTerm: "Not evaluated",
  },
} as const;

export type ProcessGlossaryTerm = keyof typeof PROCESS_GLOSSARY;
