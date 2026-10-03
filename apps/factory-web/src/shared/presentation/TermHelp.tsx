import { Toggletip, ToggletipButton, ToggletipContent } from "@carbon/react";

import { PROCESS_GLOSSARY, type ProcessGlossaryTerm } from "./processGlossary";

export function TermHelp({ term }: { term: ProcessGlossaryTerm }) {
  const entry = PROCESS_GLOSSARY[term];
  return (
    <Toggletip align="bottom">
      <ToggletipButton
        className="term-help-trigger"
        label={`${entry.label} 도움말`}
      >
        <span aria-hidden="true">?</span>
      </ToggletipButton>
      <ToggletipContent className="term-help-content">
        <p>{entry.explanation}</p>
        <small>계약 용어: {entry.contractTerm}</small>
      </ToggletipContent>
    </Toggletip>
  );
}
