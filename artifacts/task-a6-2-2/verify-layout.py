import json,re,sys
import xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
nodes=list(root.iter('node'))
parents={child:parent for parent in root.iter() for child in parent}
def find(text):
    return next(n for n in nodes if n.attrib.get('text')==text)
def rect(node):
    return list(map(int,re.findall(r'\d+',node.attrib['bounds'])))
def control(text):
    n=find(text)
    while n.attrib.get('checkable')!='true' and n in parents: n=parents[n]
    return n
def center(node):
    x1,y1,x2,y2=rect(node)
    return [(x1+x2)//2,(y1+y2)//2]
required=['开通VIP','解锁全部高级功能','会员权益','应用锁','私密相册','选择套餐','月卡','年卡','永久','¥5.9','¥19.9','¥29.9','30天','365天','永久有效','微信支付','支付宝','立即开通 ¥19.9','非自动续费，到期不自动扣费']
for text in required: find(text)
assert control('年卡').attrib['checked']=='true'
assert control('微信支付').attrib['checked']=='true'
agreement=next(n for n in nodes if n.attrib.get('checkable')=='true' and n.attrib.get('content-desc','').startswith('我已阅读'))
assert agreement.attrib['checked']=='false'
notice=find('本应用不提倡未成年支付，用户需确认自己具有完全民事行为能力。未成年用户需得到监护人同意，才可以进行支付。请监护人切实履行监护义务。')
assert all('无广告为全体用户共同享有的产品特性' not in n.attrib.get('text','') for n in nodes)
print(json.dumps({'all_primary_content_visible':True,'annual_and_wechat_default':True,'agreement_unchecked':True,'special_notice_bounds':rect(notice),'monthly_tap':center(control('月卡')),'alipay_tap':center(control('支付宝')),'agreement_tap':center(agreement),'purchase_tap':center(find('立即开通 ¥19.9'))},ensure_ascii=True))
