import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button, Result, Typography } from 'antd'

const { Paragraph } = Typography

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
}

/**
 * 全局错误边界：渲染期异常时展示可恢复的错误页，避免整页白屏。
 * <p>只能捕获渲染与生命周期异常，事件回调与异步错误仍需各自的 try/catch。</p>
 */
export default class ErrorBoundary extends Component<Props, State> {

  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('页面渲染异常:', error, info.componentStack)
  }

  handleReset = () => {
    this.setState({ error: null })
  }

  render() {
    const { error } = this.state
    if (!error) {
      return this.props.children
    }

    return (
      <Result
        status="error"
        title="页面出错了"
        subTitle="已捕获到渲染异常，可以重试或刷新页面"
        extra={[
          <Button key="retry" type="primary" onClick={this.handleReset}>
            重试
          </Button>,
          <Button key="reload" onClick={() => window.location.reload()}>
            刷新页面
          </Button>,
        ]}
      >
        <Paragraph type="secondary" style={{ marginBottom: 0 }}>
          {error.message}
        </Paragraph>
      </Result>
    )
  }
}
