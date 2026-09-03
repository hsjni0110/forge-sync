import { Component, type ReactNode } from "react";

interface AssetErrorBoundaryProps {
  children: ReactNode;
  fallback: ReactNode;
  onError: () => void;
}

interface AssetErrorBoundaryState {
  hasError: boolean;
}

export class AssetErrorBoundary extends Component<
  AssetErrorBoundaryProps,
  AssetErrorBoundaryState
> {
  state: AssetErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): AssetErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(): void {
    this.props.onError();
  }

  render(): ReactNode {
    return this.state.hasError ? this.props.fallback : this.props.children;
  }
}
