def calculate_payouts(event_id):
event = get_event(event_id)
bets = get_bets_by_event(event_id)

    total_pool = sum(bet.amount for bet in bets)
    commission = total_pool * event.commission_rate
    prize_pool = total_pool - commission
    
    winning_bets = [b for b in bets if b.option == event.result]
    winning_sum = sum(b.amount for b in winning_bets)
    
    if winning_sum == 0:
        # возврат всех ставок или иная логика
        return refund_all(bets)
    
    payouts = []
    for bet in winning_bets:
        payout_amount = (bet.amount / winning_sum) * prize_pool
        payouts.append({
            'user_id': bet.user_id,
            'bet_id': bet.id,
            'amount': payout_amount,
            'net_profit': payout_amount - bet.amount
        })
    
    # сохраняем выплаты в БД (в транзакции)
    save_payouts(payouts)
    return payouts
