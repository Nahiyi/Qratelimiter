import React from 'react';
import { createRoot } from 'react-dom/client';
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  ConfigProvider,
  Empty,
  Form,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message
} from 'antd';
import {
  ApiOutlined,
  ClearOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SaveOutlined,
  SettingOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import 'antd/dist/reset.css';
import './styles.css';

const runtimeConfig = window.__QRATELIMITER_DASHBOARD_CONFIG__ || {};
const defaultConfig = {
  basePath: '/qratelimiter/dashboard',
  apiBasePath: '/qratelimiter',
  title: 'QRateLimiter Dashboard'
};
const config = { ...defaultConfig, ...runtimeConfig };

function apiUrl(path) {
  return `${config.apiBasePath}${path}`;
}

function formatNumber(value) {
  if (value === null || value === undefined) {
    return '-';
  }
  return new Intl.NumberFormat('zh-CN').format(value);
}

function formatTime(value) {
  if (!value) {
    return '-';
  }
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}

function normalizeOption(value) {
  return value && typeof value === 'object' && 'code' in value ? value.code : value;
}

function App() {
  const [stats, setStats] = React.useState(null);
  const [settings, setSettings] = React.useState(null);
  const [loading, setLoading] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState('');
  const [lastUpdatedAt, setLastUpdatedAt] = React.useState('');
  const [form] = Form.useForm();
  const [messageApi, contextHolder] = message.useMessage();

  const loadDashboard = React.useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [statsResponse, configResponse] = await Promise.all([
        fetch(apiUrl('/stats')),
        fetch(apiUrl('/config'))
      ]);
      if (!statsResponse.ok) {
        throw new Error(`stats ${statsResponse.status}`);
      }
      if (!configResponse.ok) {
        throw new Error(`config ${configResponse.status}`);
      }
      const [nextStats, nextSettings] = await Promise.all([
        statsResponse.json(),
        configResponse.json()
      ]);
      setStats(nextStats);
      setSettings(nextSettings);
      form.setFieldsValue({
        freq: nextSettings.freq,
        interval: nextSettings.interval,
        capacity: nextSettings.capacity,
        algorithm: normalizeOption(nextSettings.algorithm),
        storage: normalizeOption(nextSettings.storage),
        strategy: 'APPLY_TO_NEW_LIMITERS_ONLY'
      });
      setLastUpdatedAt(new Date().toISOString());
    } catch (loadError) {
      setError('无法读取管理 API，请确认 clazs.ratelimiter.management.enabled=true 且 dashboard.api-base-path 配置正确。');
    } finally {
      setLoading(false);
    }
  }, [form]);

  React.useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  async function saveSettings(values) {
    setSaving(true);
    try {
      const response = await fetch(apiUrl('/config'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(values)
      });
      if (!response.ok) {
        throw new Error(`config ${response.status}`);
      }
      messageApi.success('配置已提交');
      await loadDashboard();
    } catch (saveError) {
      messageApi.error('配置提交失败，请检查参数是否合法');
    } finally {
      setSaving(false);
    }
  }

  async function clearCache(key) {
    const endpoint = key ? `/cache/${encodeURIComponent(key)}` : '/cache';
    try {
      const response = await fetch(apiUrl(endpoint), { method: 'DELETE' });
      if (!response.ok) {
        throw new Error(`cache ${response.status}`);
      }
      messageApi.success(key ? 'Key 已清理' : '缓存已清空');
      await loadDashboard();
    } catch (clearError) {
      messageApi.error('清理失败，请稍后重试');
    }
  }

  const limiters = stats?.limiters || [];
  const summaryCards = [
    {
      title: '当前 Key',
      value: stats?.currentCacheSize,
      icon: <ApiOutlined />
    },
    {
      title: '累计创建',
      value: stats?.totalCreatedLimiters,
      icon: <CloudServerOutlined />
    },
    {
      title: '放行请求',
      value: stats?.allowedRequests,
      icon: <ThunderboltOutlined />
    },
    {
      title: '拒绝请求',
      value: stats?.rejectedRequests,
      icon: <ClearOutlined />
    }
  ];

  const columns = [
    {
      title: 'Key',
      dataIndex: 'key',
      key: 'key',
      width: 190,
      render: (value) => (
        <Typography.Text code ellipsis={{ tooltip: value }} className="dashboard-key-cell">
          {value}
        </Typography.Text>
      )
    },
    {
      title: '算法',
      dataIndex: 'algorithm',
      key: 'algorithm',
      width: 130,
      render: (value) => <Tag color="blue">{normalizeOption(value) || '-'}</Tag>
    },
    {
      title: '存储',
      dataIndex: 'storage',
      key: 'storage',
      width: 92,
      render: (value) => <Tag color="green">{normalizeOption(value) || '-'}</Tag>
    },
    {
      title: '限流参数',
      key: 'limits',
      width: 150,
      render: (_, record) => (
        <Space size={4} wrap>
          <Tag>F {formatNumber(record.freq)}</Tag>
          <Tag>I {formatNumber(record.interval)}</Tag>
          <Tag>C {formatNumber(record.capacity)}</Tag>
        </Space>
      )
    },
    { title: '计数', dataIndex: 'currentCount', key: 'currentCount', width: 72, render: formatNumber },
    {
      title: '请求',
      key: 'requests',
      width: 116,
      render: (_, record) => (
        <Space size={4} wrap>
          <Tag color="green">{formatNumber(record.allowedRequests)}</Tag>
          <Tag color="red">{formatNumber(record.rejectedRequests)}</Tag>
        </Space>
      )
    },
    {
      title: '操作',
      key: 'actions',
      width: 64,
      render: (_, record) => (
        <Popconfirm
          title="清理这个 Key？"
          okText="清理"
          cancelText="取消"
          onConfirm={() => clearCache(record.key)}
        >
          <Button aria-label="清理 Key" icon={<DeleteOutlined />} />
        </Popconfirm>
      )
    }
  ];

  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#2563eb',
          borderRadius: 6,
          fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
        }
      }}
    >
      <AntApp>
        {contextHolder}
        <main className="dashboard-root bg-[#f5f7fb] text-[#182033]">
          <header className="dashboard-header border-b border-[#dce3ef] bg-white">
            <div className="mx-auto flex h-full w-full max-w-[1240px] flex-col gap-2 px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
              <div className="flex min-w-0 items-center gap-3">
                <div className="grid h-11 w-11 shrink-0 place-items-center rounded-md bg-[#2563eb] text-xl text-white">
                  <DashboardOutlined />
                </div>
                <div className="min-w-0">
                  <Typography.Title level={2} className="!m-0 !text-xl !leading-7">
                    {config.title}
                  </Typography.Title>
                  <Typography.Text className="text-[#667085]">
                    运行时限流状态、配置与缓存操作
                  </Typography.Text>
                </div>
              </div>
              <Space wrap>
                <Typography.Text className="text-[#667085]">
                  更新于 {lastUpdatedAt ? formatTime(lastUpdatedAt) : '-'}
                </Typography.Text>
                <Button icon={<ReloadOutlined />} loading={loading} onClick={loadDashboard}>
                  刷新
                </Button>
                <Popconfirm
                  title="清空全部限流缓存？"
                  okText="清空"
                  cancelText="取消"
                  onConfirm={() => clearCache()}
                >
                  <Button danger icon={<ClearOutlined />}>
                    清空缓存
                  </Button>
                </Popconfirm>
              </Space>
            </div>
          </header>

          <section className="dashboard-content mx-auto grid w-full max-w-[1320px] min-w-0 gap-4 px-4 py-4">
            {error && (
              <Alert
                className="dashboard-alert"
                showIcon
                type="warning"
                message="管理 API 暂不可用"
                description="确认 management.enabled=true，且 api-base-path 指向管理接口。"
              />
            )}

            <div className="dashboard-summary grid min-w-0 grid-cols-2 gap-4 xl:grid-cols-4">
              {summaryCards.map((item) => (
                <Card key={item.title} className="border-[#dce3ef]" variant="outlined">
                  <Statistic
                    title={<span className="text-[#667085]">{item.title}</span>}
                    value={item.value ?? 0}
                    prefix={<span className="mr-1 text-[#2563eb]">{item.icon}</span>}
                  />
                </Card>
              ))}
            </div>

            <div className="dashboard-workspace grid min-h-0 min-w-0 grid-cols-[300px_minmax(0,1fr)] gap-4">
              <Card
                title={
                  <Space>
                    <SettingOutlined />
                    <span>运行配置</span>
                  </Space>
                }
                className="dashboard-config-card min-w-0 border-[#dce3ef]"
                variant="outlined"
              >
                <Form className="dashboard-config-form" layout="vertical" form={form} onFinish={saveSettings} size="small">
                  <div className="dashboard-paths-compact">
                    <Typography.Text type="secondary">API</Typography.Text>
                    <Typography.Text code>{config.apiBasePath}</Typography.Text>
                    <Typography.Text type="secondary">Page</Typography.Text>
                    <Typography.Text code>{config.basePath}</Typography.Text>
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <Form.Item label="算法" name="algorithm">
                      <Select
                        options={[
                          { label: 'SW Log', value: 'sliding-window-log' },
                          { label: 'SW Counter', value: 'sliding-window-counter' },
                          { label: 'Token Bucket', value: 'token-bucket' },
                          { label: 'Leaky Bucket', value: 'leaky-bucket' }
                        ]}
                      />
                    </Form.Item>
                    <Form.Item label="存储" name="storage">
                      <Select
                        options={[
                          { label: 'Local', value: 'local' },
                          { label: 'Redis', value: 'redis' }
                        ]}
                      />
                    </Form.Item>
                  </div>
                  <div className="grid grid-cols-3 gap-2">
                    <Form.Item label="Freq" name="freq" rules={[{ required: true }]}>
                      <InputNumber className="w-full" min={1} />
                    </Form.Item>
                    <Form.Item label="Interval(ms)" name="interval" rules={[{ required: true }]}>
                      <InputNumber className="w-full" min={1} />
                    </Form.Item>
                    <Form.Item label="Capacity" name="capacity" rules={[{ required: true }]}>
                      <InputNumber className="w-full" min={1} />
                    </Form.Item>
                  </div>
                  <div className="dashboard-strategy-row grid grid-cols-[1fr_auto] gap-2">
                    <Form.Item label="刷新策略" name="strategy">
                      <Select
                        options={[
                          { label: '新 Key', value: 'APPLY_TO_NEW_LIMITERS_ONLY' },
                          { label: '清空缓存', value: 'CLEAR_CACHE_AND_APPLY' }
                        ]}
                      />
                    </Form.Item>
                    <Button className="dashboard-save-button" type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
                      保存
                    </Button>
                  </div>
                </Form>
              </Card>

              <Card
                title="Key 放行明细"
                className="dashboard-table-card min-w-0 border-[#dce3ef]"
                variant="outlined"
                extra={<Tag color="default">{formatNumber(limiters.length)} rows</Tag>}
              >
                <Table
                  rowKey="key"
                  loading={loading}
                  columns={columns}
                  dataSource={limiters}
                  scroll={{ y: 300 }}
                  pagination={false}
                  locale={{ emptyText: <Empty description="暂无限流 Key" /> }}
                  size="small"
                />
              </Card>
            </div>
          </section>
        </main>
      </AntApp>
    </ConfigProvider>
  );
}

createRoot(document.getElementById('root')).render(<App />);
