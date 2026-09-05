import { useId } from "react";

export function HelpTip({ text }: { text: string }) {
  const id = useId();
  return (
    <span className="help-term">
      <button type="button" className="help-icon" aria-label="용어 설명" aria-describedby={id}
        onClick={(event) => event.preventDefault()}>?</button>
      <span role="tooltip" id={id} className="help-popover">{text}</span>
    </span>
  );
}
